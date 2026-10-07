<#
.SYNOPSIS
  Drive the Connect IQ simulator without ever focusing it or touching the mouse.
.DESCRIPTION
  The simulator window is kept far off-screen and inactive. Buttons are pressed by posting
  mouse messages straight to the window, and screenshots come from PrintWindow with
  PW_RENDERFULLCONTENT (the plain call returns stale frames). Nothing here calls
  SetForegroundWindow, SetCursorPos, mouse_event or SendKeys.
.EXAMPLE
  .\garmin\tools\sim.ps1 -Start                              # launch (if needed) off-screen, inactive
  .\garmin\tools\sim.ps1 -Hide                               # move an already visible simulator off-screen
  .\garmin\tools\sim.ps1 -Device fenix5x -Click DOWN,DOWN -Out C:\temp\a.png
  .\garmin\tools\sim.ps1 -Out C:\temp\now.png                # just capture
.NOTES
  Button positions are window coordinates for each device (see $layouts); add a device
  there when a new one is needed. Clicks are applied in order,
  -Wait milliseconds apart, and the screenshot is taken after the last one.
#>
param(
    [switch]$Start,
    [switch]$Hide,
    [string[]]$Click = @(),
    [string]$Device = "fenix5x",
    [string]$Out = "",
    [int]$Wait = 900,
    [switch]$Info
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public class SimWin {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] public struct WINDOWPLACEMENT {
        public int length, flags, showCmd; public POINT ptMin, ptMax; public RECT rcNormal;
    }
    [StructLayout(LayoutKind.Sequential)] public struct STARTUPINFO {
        public int cb; public string lpReserved, lpDesktop, lpTitle;
        public int dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars, dwFillAttribute, dwFlags;
        public short wShowWindow, cbReserved2; public IntPtr lpReserved2, hStdInput, hStdOutput, hStdError;
    }
    [StructLayout(LayoutKind.Sequential)] public struct PROCESS_INFORMATION {
        public IntPtr hProcess, hThread; public int dwProcessId, dwThreadId;
    }
    delegate bool EnumProc(IntPtr h, IntPtr l);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc p, IntPtr l);
    [DllImport("user32.dll")] static extern bool EnumChildWindows(IntPtr h, EnumProc p, IntPtr l);
    [DllImport("user32.dll")] static extern int GetWindowThreadProcessId(IntPtr h, out int pid);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool GetWindowPlacement(IntPtr h, ref WINDOWPLACEMENT p);
    [DllImport("user32.dll")] public static extern bool SetWindowPlacement(IntPtr h, ref WINDOWPLACEMENT p);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint f);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern IntPtr ChildWindowFromPointEx(IntPtr h, POINT p, uint flags);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern int GetWindowTextLength(IntPtr h);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CreateProcess(string app, string cmd, IntPtr pa, IntPtr ta, bool inherit, uint flags,
        IntPtr env, string dir, ref STARTUPINFO si, out PROCESS_INFORMATION pi);

    // The simulator's main window: the largest visible top-level window of the process.
    public static IntPtr FindMain(int pid) {
        IntPtr best = IntPtr.Zero; long bestArea = 0;
        EnumWindows((h, l) => {
            int p; GetWindowThreadProcessId(h, out p);
            if (p != pid || !IsWindowVisible(h)) return true;
            RECT r; GetWindowRect(h, out r);
            long a = (long)(r.Right - r.Left) * (r.Bottom - r.Top);
            if (GetWindowTextLength(h) > 0 && a > bestArea) { best = h; bestArea = a; }
            return true;
        }, IntPtr.Zero);
        return best;
    }

    // Launch without activating: shown minimized and inactive (SW_SHOWMINNOACTIVE = 7).
    public static int StartInactive(string exe) {
        STARTUPINFO si = new STARTUPINFO(); si.cb = Marshal.SizeOf(si);
        si.dwFlags = 1; si.wShowWindow = 7;   // STARTF_USESHOWWINDOW
        PROCESS_INFORMATION pi;
        if (!CreateProcess(exe, null, IntPtr.Zero, IntPtr.Zero, false, 0, IntPtr.Zero, System.IO.Path.GetDirectoryName(exe), ref si, out pi))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        return pi.dwProcessId;
    }

    // Restore (SW_SHOWNOACTIVATE = 4) and move far off-screen in a single atomic call.
    public static void ParkOffScreen(IntPtr h) {
        WINDOWPLACEMENT wp = new WINDOWPLACEMENT(); wp.length = Marshal.SizeOf(wp);
        GetWindowPlacement(h, ref wp);
        int w = wp.rcNormal.Right - wp.rcNormal.Left, ht = wp.rcNormal.Bottom - wp.rcNormal.Top;
        wp.rcNormal.Left = -3000; wp.rcNormal.Top = 0; wp.rcNormal.Right = -3000 + w; wp.rcNormal.Bottom = ht;
        wp.showCmd = 4;
        SetWindowPlacement(h, ref wp);
        // SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE
        SetWindowPos(h, IntPtr.Zero, -3000, 0, 0, 0, 0x0001 | 0x0004 | 0x0010);
    }
}
"@
# Deliberately NOT DPI-aware: the simulator window is DPI-unaware, so its own coordinates are\n# "logical" pixels and a DPI-aware caller would see 1.5x sizes that do not match what it draws.

function Get-SimProcess { Get-Process simulator -ErrorAction SilentlyContinue | Select-Object -First 1 }

function Get-SimWindow([int]$timeoutSec = 30) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    do {
        $p = Get-SimProcess
        if ($p) {
            $h = [SimWin]::FindMain($p.Id)
            if ($h -ne [IntPtr]::Zero) { return $h }
        }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    throw "Simulator window not found."
}

function Move-SimOffScreen([IntPtr]$h) {
    $r = New-Object SimWin+RECT
    [SimWin]::GetWindowRect($h, [ref]$r) | Out-Null
    if ($r.Left -gt -2000 -or [SimWin]::IsIconic($h)) { [SimWin]::ParkOffScreen($h) }
}

# Button positions in window coordinates (the pixels of a -Out screenshot, which captures the
# whole window), measured on the simulator's drawing of each watch. The simulator does not
# resize its window per device, so these are fixed per device.
$layouts = @{
    "fenix5x" = @{ START = @(440, 268); BACK = @(418, 440); UP = @(55, 350); DOWN = @(64, 418) }
    "fenix7s" = @{ START = @(344, 214); BACK = @(334, 398); UP = @(30, 300); DOWN = @(50, 392) }
    "fenix7"  = @{ START = @(378, 218); BACK = @(378, 432); UP = @(28, 322); DOWN = @(45, 432) }
    "epix2pro47mm" = @{ START = @(612, 332); BACK = @(608, 655); UP = @(33, 470); DOWN = @(60, 612) }
    "fr265"   = @{ START = @(560, 362); BACK = @(562, 628); UP = @(48, 480); DOWN = @(80, 615) }
}
if ($Start -or $Hide) {
    if (-not (Get-SimProcess)) {
        if (-not $Start) { throw "Simulator is not running." }
        $exe = Get-ChildItem "$env:APPDATA\Garmin\ConnectIQ\Sdks" -Directory | Sort-Object Name | Select-Object -Last 1 |
            ForEach-Object { Join-Path $_.FullName "bin\simulator.exe" }
        [SimWin]::StartInactive($exe) | Out-Null
    }
    $h = Get-SimWindow
    Move-SimOffScreen $h
}

if ($Info -or $Click.Count -gt 0 -or $Out -ne "") {
    $h = Get-SimWindow 5
    $wr = New-Object SimWin+RECT; [SimWin]::GetWindowRect($h, [ref]$wr) | Out-Null
    $cr = New-Object SimWin+RECT; [SimWin]::GetClientRect($h, [ref]$cr) | Out-Null
    if ($Info) {
        "hwnd=$h window=($($wr.Left),$($wr.Top),$($wr.Right),$($wr.Bottom)) client=$($cr.Right)x$($cr.Bottom) iconic=$([SimWin]::IsIconic($h))"
    }
    foreach ($name in $Click) {
        $pos = $layouts[$Device][$name.ToUpper()]
        if (-not $pos) { throw "Unknown button '$name' for device '$Device'." }
        # Window coordinates to client coordinates.
        $origin = New-Object SimWin+POINT
        [SimWin]::ClientToScreen($h, [ref]$origin) | Out-Null
        $x = $pos[0] - ($origin.X - $wr.Left); $y = $pos[1] - ($origin.Y - $wr.Top)
        # Deliver to the control under the point when there is one, with its own client coordinates.
        $target = $h
        $pt = New-Object SimWin+POINT; $pt.X = $x; $pt.Y = $y
        $child = [SimWin]::ChildWindowFromPointEx($h, $pt, 0)   # CWP_ALL
        $cx = $x; $cy = $y
        if ($child -ne [IntPtr]::Zero -and $child -ne $h) {
            $sp = New-Object SimWin+POINT; $sp.X = $x; $sp.Y = $y
            [SimWin]::ClientToScreen($h, [ref]$sp) | Out-Null
            $cwr = New-Object SimWin+RECT; [SimWin]::GetWindowRect($child, [ref]$cwr) | Out-Null
            $target = $child; $cx = $sp.X - $cwr.Left; $cy = $sp.Y - $cwr.Top
        }
        $lp = [IntPtr](($cy -shl 16) -bor ($cx -band 0xFFFF))
        [SimWin]::PostMessage($target, 0x0200, [IntPtr]::Zero, $lp) | Out-Null       # WM_MOUSEMOVE
        [SimWin]::PostMessage($target, 0x0201, [IntPtr]1, $lp) | Out-Null           # WM_LBUTTONDOWN
        Start-Sleep -Milliseconds 120
        [SimWin]::PostMessage($target, 0x0202, [IntPtr]::Zero, $lp) | Out-Null      # WM_LBUTTONUP
        Start-Sleep -Milliseconds $Wait
    }
    if ($Out -ne "") {
        $ww = $wr.Right - $wr.Left; $wh = $wr.Bottom - $wr.Top
        if ($cr.Right -le 0 -or [SimWin]::IsIconic($h)) { throw "Simulator window is minimized; it cannot render." }
        $bmp = New-Object System.Drawing.Bitmap $ww, $wh
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $dc = $g.GetHdc()
        $ok = [SimWin]::PrintWindow($h, $dc, 2)   # PW_RENDERFULLCONTENT
        $g.ReleaseHdc($dc)
        New-Item -ItemType Directory -Force (Split-Path -Parent $Out) | Out-Null
        $bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
        "Saved $Out ($ww x $wh, PrintWindow=$ok)"
    }
}
