# Helpers for driving the test phone safely (source it). Every tap checks that no call is active and
# that the expected app is in front, and stops otherwise.
export MSYS_NO_PATHCONV=1
# uiautomator dumps are UTF-8; without this Python on Windows reads them as cp1252 and Cyrillic never matches.
export PYTHONUTF8=1
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
SHOTS="${SHOTS:-/tmp}"

guard() { # guard <package-substring>
  local call focus
  call=$("$ADB" shell dumpsys telephony.registry | grep -m1 mCallState | tr -d ' \r')
  [ "$call" = "mCallState=0" ] || { echo "ABORT: call state $call"; return 1; }
  focus=$("$ADB" shell dumpsys window | grep mCurrentFocus | tr -d '\r')
  case "$focus" in *"$1"*) return 0 ;; esac
  echo "ABORT: focus is $focus, wanted $1"; return 1
}

# tap_text <package> <text>: finds the text on screen with uiautomator and taps its centre.
tap_text() {
  guard "$1" || return 1
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b
  b=$("$ADB" exec-out cat /sdcard/ui.xml | python -c "
import sys,re
x=sys.stdin.read(); t=sys.argv[1]
nodes=[m.group(0) for m in re.finditer(r'<node [^>]*>', x)]
# An exact label wins over one that only starts with it.
hits=[n for n in nodes if ('text=\"'+t+'\"') in n or ('content-desc=\"'+t+'\"') in n] or [n for n in nodes if ('text=\"'+t) in n or ('content-desc=\"'+t) in n]
if hits:
    a=list(map(int,re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',hits[0]).groups()))
    print((a[0]+a[2])//2,(a[1]+a[3])//2)
" "$2")
  [ -n "$b" ] || { echo "ABORT: no '$2' on screen"; return 1; }
  "$ADB" shell input tap $b
}

# tap_at <package> <x> <y>
tap_at() { guard "$1" && "$ADB" shell input tap "$2" "$3"; }

shot() { "$ADB" exec-out screencap -p > "$SHOTS/$1.png"; }
