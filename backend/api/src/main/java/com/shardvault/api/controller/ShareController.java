package com.shardvault.api.controller;

import com.shardvault.api.security.UserPrincipal;
import com.shardvault.api.service.ChunkReplicaReader;
import com.shardvault.api.service.ShareService;
import com.shardvault.api.service.TransferMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;

import java.util.List;
import java.util.UUID;

@RestController
public class ShareController {
    private final ShareService shares;
    private final ChunkReplicaReader chunks;
    private final JdbcTemplate jdbc;
    private final TransferMetrics metrics;

    public ShareController(ShareService shares, ChunkReplicaReader chunks, JdbcTemplate jdbc, TransferMetrics metrics) {
        this.shares = shares; this.chunks = chunks; this.jdbc = jdbc; this.metrics = metrics;
    }

    public record CreateRequest(@jakarta.validation.constraints.NotNull UUID fileId,
                                java.time.Instant expiresAt,
                                @Positive Integer maxDownloads,
                                @jakarta.validation.constraints.Size(min = 8, max = 128) String password) {}

    @PostMapping("/api/v1/shares")
    public ResponseEntity<ShareService.CreatedShare> create(@Valid @RequestBody CreateRequest request,
                                                             Authentication auth, HttpServletRequest http) {
        UUID owner = userId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(shares.create(owner, request.fileId(), request.expiresAt(),
                request.maxDownloads(), request.password(), http.getRemoteAddr(), http.getHeader("User-Agent")));
    }

    @DeleteMapping("/api/v1/shares/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication auth, HttpServletRequest http) {
        shares.revoke(userId(auth), id, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/s/{token}")
    public ShareService.ShareMetadata metadata(@PathVariable String token,
            @RequestHeader(name = "X-Share-Password", required = false) String password, HttpServletRequest http) {
        return shares.metadata(token, password, http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    @GetMapping("/s/{token}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String token,
            @RequestHeader(name = "X-Share-Password", required = false) String password,
            @RequestHeader(name = HttpHeaders.RANGE, required = false) String range,
            HttpServletRequest http) {
        ShareService.SharedDownload shared = shares.consume(token, password, http.getRemoteAddr(), http.getHeader("User-Agent"));
        List<ChunkRange> fileChunks = jdbc.query("select id, plain_size + 16 from chunks where file_id = ? order by idx",
                (rs, row) -> new ChunkRange(rs.getObject(1, UUID.class), rs.getLong(2)), shared.fileId());
        long totalLength = fileChunks.stream().mapToLong(ChunkRange::length).sum();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(safeFilename(shared.fileName())).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        long start = 0, end = totalLength - 1;
        HttpStatus status = HttpStatus.OK;
        if (range == null || range.isBlank()) {
            headers.setContentLength(totalLength);
        } else {
            long[] bounds = parseRange(range, totalLength);
            start = bounds[0]; end = bounds[1]; status = HttpStatus.PARTIAL_CONTENT;
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalLength);
            headers.setContentLength(end - start + 1);
        }
        final long selectedStart = start, selectedEnd = end;
        StreamingResponseBody body = output -> {
            long offset = 0;
            for (ChunkRange chunk : fileChunks) {
                long chunkStart = offset, chunkEnd = offset + chunk.length() - 1;
                offset += chunk.length();
                if (chunkEnd < selectedStart || chunkStart > selectedEnd) continue;
                byte[] data = chunks.readVerified(chunk.id());
                int from = (int) Math.max(0, selectedStart - chunkStart);
                int to = (int) Math.min(data.length, selectedEnd - chunkStart + 1);
                output.write(data, from, to - from);
            }
            metrics.recordDownloadBytes(selectedEnd - selectedStart + 1);
        };
        return new ResponseEntity<>(body, headers, status);
    }

    private static long[] parseRange(String range, long length) {
        if (length == 0 || !range.startsWith("bytes=") || range.contains(",")) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Unsupported byte range");
        String[] parts = range.substring(6).split("-", -1);
        if (parts.length != 2) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid byte range");
        try {
            long start;
            long end;
            if (parts[0].isBlank()) {
                long suffix = Long.parseLong(parts[1]);
                if (suffix <= 0) throw new NumberFormatException();
                start = Math.max(0, length - suffix);
                end = length - 1L;
            } else {
                start = Long.parseLong(parts[0]);
                end = parts.length > 1 && !parts[1].isBlank() ? Long.parseLong(parts[1]) : length - 1L;
            }
            if (start < 0 || start >= length || end < start) throw new NumberFormatException();
            return new long[]{start, Math.min(end, length - 1L)};
        } catch (NumberFormatException ex) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid byte range");
        }
    }

    private record ChunkRange(UUID id, long length) {}

    private static String safeFilename(String input) {
        if (input == null || input.isBlank()) return "download.bin";
        String leaf = input.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\p{Cntrl}]", "_").trim();
        return leaf.isBlank() || leaf.equals(".") || leaf.equals("..") ? "download.bin" : leaf;
    }

    private static UUID userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) return UUID.fromString(principal.getId());
        throw new org.springframework.security.access.AccessDeniedException("Authentication required");
    }
}
