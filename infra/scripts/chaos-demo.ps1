param(
    [Parameter(Mandatory = $true)][string]$UploadUrl,
    [Parameter(Mandatory = $true)][string]$DownloadUrl,
    [Parameter(Mandatory = $true)][string]$MinioContainer,
    [Parameter(Mandatory = $true)][string]$AuthToken
)

$ErrorActionPreference = 'Stop'
$headers = @{ Authorization = "Bearer $AuthToken" }
$tempPath = Join-Path ([System.IO.Path]::GetTempPath()) ("shardvault-chaos-" + [guid]::NewGuid() + '.bin')
$restored = $false
try {
    # The supplied upload endpoint must return a resource id or download URL.
    $payload = [byte[]]::new(1024 * 1024)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($payload)
    [System.IO.File]::WriteAllBytes($tempPath, $payload)
    $uploadResult = Invoke-RestMethod -Method Post -Uri $UploadUrl -Headers $headers -Form @{ file = Get-Item $tempPath }
    $resolvedDownloadUrl = if ($uploadResult.downloadUrl) {
        [string]$uploadResult.downloadUrl
    } elseif ($uploadResult.fileId) {
        $DownloadUrl.Replace('{id}', [uri]::EscapeDataString([string]$uploadResult.fileId))
    } else {
        throw 'Upload response must contain downloadUrl or fileId'
    }

    docker stop $MinioContainer | Out-Null
    try {
        $download = Invoke-WebRequest -Uri $resolvedDownloadUrl -Headers $headers -OutFile ($tempPath + '.download') -PassThru
        if ($download.StatusCode -ne 200 -and $download.StatusCode -ne 206) { throw "Download failed: $($download.StatusCode)" }
        $expected = (Get-FileHash $tempPath -Algorithm SHA256).Hash
        $actual = (Get-FileHash ($tempPath + '.download') -Algorithm SHA256).Hash
        if ($expected -ne $actual) { throw 'Downloaded bytes differ from uploaded bytes' }
        $restored = $true
        Write-Host 'Failover download checksum verified while MinIO was stopped.'
    }
    finally {
        docker start $MinioContainer | Out-Null
    }
} finally {
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath ($tempPath + '.download') -Force -ErrorAction SilentlyContinue
}
if (-not $restored) { throw 'Chaos demo did not complete successfully.' }
