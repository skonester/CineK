# Nucleus has a small launcher/helper window; Process.MainWindowHandle may select it.
if (-not ('CineKDesktopWindow' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class CineKDesktopWindow {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
    private delegate bool EnumWindowProc(IntPtr hwnd, IntPtr param);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumWindowProc proc, IntPtr param);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out Rect rect);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hwnd, uint msg, IntPtr wparam, IntPtr lparam);
    public static IntPtr FindMainWindow(uint processId) {
        IntPtr found = IntPtr.Zero;
        EnumWindows((hwnd, param) => {
            uint pid;
            GetWindowThreadProcessId(hwnd, out pid);
            Rect rect;
            if (pid == processId && IsWindowVisible(hwnd) && GetWindowRect(hwnd, out rect) &&
                rect.Right - rect.Left >= 800 && rect.Bottom - rect.Top >= 400) {
                found = hwnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }
}
'@
}
