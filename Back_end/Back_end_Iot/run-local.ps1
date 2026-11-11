param(
    [string]$DbUsername
)

$ErrorActionPreference = 'Stop'
$firmwareSecrets = Join-Path $PSScriptRoot '..\..\sketch_sep11a\secrets.h'

function Get-MqttSetting([string]$name) {
    if (-not (Test-Path -LiteralPath $firmwareSecrets)) {
        throw 'Không tìm thấy sketch_sep11a\secrets.h để đọc cấu hình MQTT còn thiếu.'
    }
    $content = Get-Content -LiteralPath $firmwareSecrets -Raw
    $pattern = '(?m)^\s*constexpr char ' + [regex]::Escape($name) + '\[\]\s*=\s*"([^"]*)";'
    $match = [regex]::Match($content, $pattern)
    if (-not $match.Success) {
        throw "Thiếu $name trong secrets.h."
    }
    return $match.Groups[1].Value
}

function Read-SecretText([string]$prompt) {
    $secure = Read-Host -Prompt $prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Get-ConfiguredValue([string]$name) {
    foreach ($scope in 'Process', 'User', 'Machine') {
        $value = [Environment]::GetEnvironmentVariable($name, $scope)
        if (-not [string]::IsNullOrEmpty($value)) {
            return $value
        }
    }
    return $null
}

function Set-IfMissing([string]$name, [scriptblock]$fallback) {
    $value = Get-ConfiguredValue $name
    if ($null -eq $value) {
        $value = & $fallback
    }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

$names = @(
    'IOT_JWT_SECRET', 'IOT_JWT_ISSUER', 'IOT_JWT_TTL_SECONDS', 'JAVA_HOME', 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'IOT_ADMIN_PASSWORD',
    'IOT_MQTT_HOST', 'IOT_MQTT_PORT', 'IOT_MQTT_USERNAME', 'IOT_MQTT_PASSWORD',
    'SPRING_JPA_HIBERNATE_DDL_AUTO'
)
$original = @{}
foreach ($name in $names) {
    $original[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

$javaHome = Get-ConfiguredValue 'JAVA_HOME'
if ($null -eq $javaHome) {
    $javaHome = 'C:\Program Files\Java\jdk-17'
}
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
    throw "Không tìm thấy JDK tại $javaHome."
}

try {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'Process')
    Set-IfMissing 'IOT_JWT_SECRET' { '' }
    Set-IfMissing 'IOT_JWT_ISSUER' { 'iot-command-center' }
    Set-IfMissing 'IOT_JWT_TTL_SECONDS' { '3600' }
    Set-IfMissing 'DB_URL' { 'jdbc:mysql://localhost:3306/iot_db' }
    if ([string]::IsNullOrEmpty($DbUsername)) {
        Set-IfMissing 'DB_USERNAME' { 'root' }
    } else {
        [Environment]::SetEnvironmentVariable('DB_USERNAME', $DbUsername, 'Process')
    }
    Set-IfMissing 'DB_PASSWORD' { Read-SecretText 'Mật khẩu MySQL' }
    Set-IfMissing 'IOT_ADMIN_PASSWORD' { Read-SecretText 'Mật khẩu admin ban đầu (Enter nếu đã có tài khoản)' }
    Set-IfMissing 'IOT_MQTT_HOST' { 'localhost' }
    Set-IfMissing 'IOT_MQTT_PORT' { '1001' }
    Set-IfMissing 'IOT_MQTT_USERNAME' { Get-MqttSetting 'MQTT_USERNAME' }
    Set-IfMissing 'IOT_MQTT_PASSWORD' { Get-MqttSetting 'MQTT_PASSWORD' }
    [Environment]::SetEnvironmentVariable('SPRING_JPA_HIBERNATE_DDL_AUTO', 'validate', 'Process')

    & (Join-Path $PSScriptRoot 'mvnw.cmd') spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Backend dừng với mã lỗi $LASTEXITCODE."
    }
} finally {
    foreach ($name in $names) {
        [Environment]::SetEnvironmentVariable($name, $original[$name], 'Process')
    }
}
