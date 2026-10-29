package com.shardvault.storage;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProviderRegistry {
    private static final String ENABLED_PROVIDERS_SQL = """
            select id, type, config_ref, max_chunk_bytes, max_object_bytes
            from providers
            where enabled = true
            order by id
            """;

    private final DataSource dataSource;
    private final ProviderConfigResolver configResolver;
    private final ReliabilityConfig reliabilityConfig;
    private final Map<String, ProviderTelemetry> telemetry = new ConcurrentHashMap<>();

    public ProviderRegistry(DataSource dataSource) {
        this(dataSource, new EnvironmentProviderConfigResolver(), ReliabilityConfig.defaults());
    }

    public ProviderRegistry(DataSource dataSource, ProviderConfigResolver configResolver, ReliabilityConfig reliabilityConfig) {
        this.dataSource = dataSource;
        this.configResolver = configResolver;
        this.reliabilityConfig = reliabilityConfig;
    }

    public List<StorageProvider> loadEnabledProviders() {
        List<StorageProvider> providers = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(ENABLED_PROVIDERS_SQL);
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                StorageProvider provider = toProvider(row(resultSet));
                providers.add(new ReliableStorageProvider(new MonitoredStorageProvider(provider, telemetry(provider.id())), reliabilityConfig));
            }
            return List.copyOf(providers);
        } catch (SQLException ex) {
            throw new ProviderConfigurationException("Failed to load providers from database", ex);
        }
    }

    public Optional<StorageProvider> loadProvider(String id) {
        String sql = "select id, type, config_ref, max_chunk_bytes, max_object_bytes from providers where id = ?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return Optional.empty();
                StorageProvider provider = toProvider(row(resultSet));
                return Optional.of(new ReliableStorageProvider(new MonitoredStorageProvider(provider, telemetry(provider.id())), reliabilityConfig));
            }
        } catch (SQLException ex) {
            throw new ProviderConfigurationException("Failed to load provider " + id, ex);
        }
    }

    public Map<String, ProviderTelemetry> telemetrySnapshot() { return Map.copyOf(telemetry); }

    public void initializeTelemetry(Iterable<String> providerIds) {
        providerIds.forEach(this::telemetry);
    }

    public StorageRouter router(StorageRouter.Weights weights, Map<String, Double> costs) {
        return new StorageRouter(weights, telemetry, costs);
    }

    private ProviderTelemetry telemetry(String id) {
        return telemetry.computeIfAbsent(id, key -> new ProviderTelemetry(key, 5, java.time.Duration.ofSeconds(30)));
    }

    private StorageProvider toProvider(ProviderRow row) {
        ProviderConfig config = configResolver.resolve(row.configRef());
        ProviderLimits limits = new ProviderLimits(
                Optional.ofNullable(row.maxChunkBytes()),
                Optional.ofNullable(row.maxObjectBytes()),
                config.optional("rateLimitHint"));

        ProviderType type = ProviderType.valueOf(row.type().toUpperCase(Locale.ROOT));
        return switch (type) {
            case LOCAL_FS -> new LocalFsProvider(row.id(), Path.of(config.require("root")), limits);
            case S3_COMPATIBLE, BACKBLAZE_B2, CLOUDFLARE_R2 -> new S3CompatibleProvider(new S3CompatibleProviderConfig(
                    row.id(),
                    config.uri("endpointOverride"),
                    config.require("region"),
                    config.require("bucket"),
                    config.bool("pathStyleAccess", true),
                    config.require("accessKeyId"),
                    config.require("secretAccessKey"),
                    limits), type);
            case GCS -> new GcsProvider(row.id(), config.require("bucket"), limits);
            case TELEGRAM_BOT -> new TelegramBotProvider(row.id(), config.require("botToken"), config.require("chatId"), limits);
        };
    }

    private static ProviderRow row(ResultSet resultSet) throws SQLException {
        Long maxChunkBytes = nullableLong(resultSet, "max_chunk_bytes");
        Long maxObjectBytes = nullableLong(resultSet, "max_object_bytes");
        return new ProviderRow(
                resultSet.getString("id"),
                resultSet.getString("type"),
                resultSet.getString("config_ref"),
                maxChunkBytes,
                maxObjectBytes);
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private record ProviderRow(String id, String type, String configRef, Long maxChunkBytes, Long maxObjectBytes) {
    }
}
