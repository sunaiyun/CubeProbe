param(
    [string]$VersionName = "1.0.0",
    [int]$VersionCode = 1
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "D:\AndroidSDK" }
$aapt2 = Join-Path $sdk "build-tools\36.0.0\aapt2.exe"
$zipalign = Join-Path $sdk "build-tools\36.0.0\zipalign.exe"
$apksigner = Join-Path $sdk "build-tools\36.0.0\apksigner.bat"
$androidJar = Join-Path $sdk "platforms\android-37.0\android.jar"
$d8 = Join-Path $sdk "build-tools\36.0.0\d8.bat"
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot" }
$javac = Join-Path $javaHome "bin\javac.exe"
$keytool = Join-Path $javaHome "bin\keytool.exe"
$jar = Join-Path $javaHome "bin\jar.exe"

$res = Join-Path $root "app\src\main\res"
$src = Join-Path $root "app\src\main\java"
$manifest = Join-Path $root "app\src\main\AndroidManifest.xml"
$build = Join-Path $root "build"
$gen = Join-Path $build "generated"
$classes = Join-Path $build "classes"
$dexDir = Join-Path $build "dex"
$resZip = Join-Path $build "resources.zip"
$unsigned = Join-Path $build "app-unsigned.apk"
$withDex = Join-Path $build "app-with-dex.apk"
$aligned = Join-Path $build "app-aligned.apk"
$keystore = Join-Path $root "signing\cubeprobe-release.jks"
$dist = Join-Path $root "dist"
$finalApk = Join-Path $dist "CubeProbe-$VersionName.apk"

foreach ($tool in @($aapt2, $zipalign, $apksigner, $androidJar, $d8, $javac, $keytool, $jar)) {
    if (-not (Test-Path $tool)) {
        throw "Required build tool is missing: $tool"
    }
}

foreach ($dir in @($build, $gen, $classes, $dexDir, $dist)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keystore) | Out-Null
Get-ChildItem $gen -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
Get-ChildItem $classes -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
Get-ChildItem $dexDir -Recurse -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
foreach ($old in @($resZip, $unsigned, $withDex, $aligned, $finalApk)) {
    if (Test-Path $old) { Remove-Item $old -Force }
}

Write-Host "[1/7] Compiling Android resources"
& $aapt2 compile --dir $res -o $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/7] Linking resources and manifest"
& $aapt2 link -o $unsigned -I $androidJar --manifest $manifest `
    --java $gen --auto-add-overlay `
    --min-sdk-version 24 --target-sdk-version 35 `
    --version-code $VersionCode --version-name $VersionName `
    $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/7] Compiling Java sources against Android API 37"
$javaFiles = @()
$javaFiles += Get-ChildItem -Path $src -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$javaFiles += Get-ChildItem -Path $gen -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$javacArgs = @(
    "-encoding", "UTF-8",
    "-source", "8", "-target", "8",
    "-classpath", $androidJar,
    "-Xlint:-options",
    "-nowarn",
    "-d", $classes
) + $javaFiles
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& $javac @javacArgs
$javacExit = $LASTEXITCODE
$ErrorActionPreference = $previousErrorAction
if ($javacExit -ne 0) { throw "javac failed" }

Write-Host "[4/7] Dexing classes"
$classFiles = Get-ChildItem -Path $classes -Recurse -Filter *.class | Select-Object -ExpandProperty FullName
$d8Args = @("--release", "--min-api", "24", "--lib", $androidJar, "--output", $dexDir) + $classFiles
& $d8 @d8Args
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/7] Packaging and aligning"
Copy-Item $unsigned $withDex -Force
& $jar uf $withDex -C $dexDir classes.dex
if ($LASTEXITCODE -ne 0) { throw "jar failed to add classes.dex" }
& $zipalign -f 4 $withDex $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

$storePass = $env:CUBEPROBE_KEYSTORE_PASS
if (-not $storePass) { throw "Set CUBEPROBE_KEYSTORE_PASS before building (keystore password)." }

Write-Host "[6/7] Preparing signing key"
if (-not (Test-Path $keystore)) {
    & $keytool -genkeypair -v -keystore $keystore -storepass $storePass `
        -keypass $storePass -alias cubeprobe -keyalg RSA -keysize 2048 `
        -validity 10000 -dname "CN=Cube Probe, OU=Mobile, O=CubeProbe, L=Shanghai, ST=Shanghai, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

Write-Host "[7/7] Signing APK"
& $apksigner sign --ks $keystore --ks-key-alias cubeprobe `
    --ks-pass "pass:$storePass" --key-pass "pass:$storePass" `
    --min-sdk-version 24 --v1-signing-enabled true --v2-signing-enabled true `
    --v3-signing-enabled true --out $finalApk $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
& $apksigner verify --verbose --print-certs $finalApk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }

$size = [math]::Round((Get-Item $finalApk).Length / 1MB, 2)
Write-Host ""
Write-Host "Built: $finalApk ($size MB)" -ForegroundColor Green
