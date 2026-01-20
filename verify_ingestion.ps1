$ErrorActionPreference = "Stop"

# URLs
$baseUrl = "http://localhost:8081/api/batches"
$batchId = "test-batch-" + (Get-Date -Format "yyyyMMddHHmmss")

Write-Host "--- Starting Verification for Batch: $batchId ---"

# 1. Create dummy files
$dataFile = "test-data.bin"
$metaFile = "test-meta.json"
Set-Content -Path $dataFile -Value "Binary content simulation"
Set-Content -Path $metaFile -Value "{ ""meta"": ""data"", ""batchId"": ""$batchId"" }"

# 2. Upload Files using curl (easier for multipart in generic shells vs PS version diffs)
Write-Host "`n1. Uploading Files..."
curl -F "file=@$dataFile" "$baseUrl/$batchId/upload"
curl -F "file=@$metaFile" "$baseUrl/$batchId/upload"

# 3. Complete Batch
Write-Host "`n`n2. Completing Batch..."
$response = Invoke-RestMethod -Uri "$baseUrl/$batchId/complete" -Method Post
Write-Host "Response: $response"

# 4. Cleanup
Remove-Item $dataFile
Remove-Item $metaFile

Write-Host "`n--- Verification Script Complete ---"
