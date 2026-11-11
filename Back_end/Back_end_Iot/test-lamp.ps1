param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('lamp1', 'lamp2')]
    [string]$DeviceKey,

    [Parameter(Mandatory = $true)]
    [ValidateSet('ON', 'OFF')]
    [string]$Action,

    [string]$Username = 'admin',
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$uri = [Uri]$BaseUrl
if ($uri.Scheme -ne 'http' -or $uri.Host -notin @('localhost', '127.0.0.1')) {
    throw 'Chỉ gửi tài khoản tới backend trên chính laptop (localhost hoặc 127.0.0.1).'
}

$secure = Read-Host -Prompt "Mật khẩu đăng nhập $Username" -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}

$base = $BaseUrl.TrimEnd('/')
try {
    $loginBody = @{ username = $Username; password = $password } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" `
        -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($loginBody))
} finally {
    $password = $null
    $loginBody = $null
}
$headers = @{ Authorization = 'Bearer ' + $login.accessToken }

$devices = @(Invoke-RestMethod -Uri "$base/api/devices" -Headers $headers)
$device = $devices | Where-Object { $_.deviceKey -eq $DeviceKey } | Select-Object -First 1
if ($null -eq $device) { throw "Không tìm thấy thiết bị $DeviceKey." }
$body = @{ deviceId = $device.id; action = $Action } | ConvertTo-Json -Compress
$receipt = Invoke-RestMethod -Method Post -Uri "$base/api/devices/$($device.id)/control" `
    -Headers $headers -ContentType 'application/json' -Body $body
Write-Host "Đã gửi $Action cho $DeviceKey; requestId: $($receipt.requestId)"

for ($attempt = 0; $attempt -lt 12; $attempt++) {
    Start-Sleep -Milliseconds 500
    $result = Invoke-RestMethod -Uri "$base/api/commands/$($receipt.requestId)" `
        -Headers $headers
    if ($result.status -ne 'PENDING') {
        Write-Host "Kết quả: $($result.status)"
        return
    }
}

Write-Host 'Lệnh vẫn đang PENDING; kiểm tra lại sau vài giây.'
