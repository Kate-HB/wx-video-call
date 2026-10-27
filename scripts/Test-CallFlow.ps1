param(
    [Parameter(Mandatory = $true)][string]$Contact,
    [string]$Adb = 'D:\Soft\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
& $Adb logcat -c
& $Adb shell am start -n com.forolderr.wxvideo/.CallFlowActivity --es contact $Contact --ez dryRun true
if ($LASTEXITCODE -ne 0) { throw 'Cannot launch test' }
$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 500
    $log = (& $Adb logcat -d -s WxVideoCall:D) -join "`n"
    if ($log -match 'video confirm dispatched|clicked video confirm') {
        throw 'Dry run dispatched a call'
    }
    if ($log -match 'dryRun: stopped before final call') { break }
} while ((Get-Date) -lt $deadline)
$log
if ($log -notmatch 'dryRun: stopped before final call') { throw 'Call flow did not reach final menu' }
& $Adb shell screencap -p /sdcard/wx-flow-test.png
& $Adb pull /sdcard/wx-flow-test.png "$PSScriptRoot\..\build\wx-flow-test.png"
if ($LASTEXITCODE -ne 0) { throw 'Cannot capture final menu' }
'PASS: dry run completed; inspect build/wx-flow-test.png for the correct contact and menu.'
