using System.Runtime.InteropServices;

namespace MyMouse.Host.Ui;

/// <summary>Declaraciones Win32 de las ventanas: estilos, monitor y barra de tareas, y el hook de teclado.</summary>
internal static class NativeUi
{
    public const int GWL_EXSTYLE = -20;
    public const long WS_EX_TOPMOST = 0x00000008;
    public const long WS_EX_TRANSPARENT = 0x00000020;
    public const long WS_EX_TOOLWINDOW = 0x00000080;
    public const long WS_EX_NOACTIVATE = 0x08000000;

    public static readonly IntPtr HWND_TOPMOST = new(-1);
    public const uint SWP_NOACTIVATE = 0x0010;
    public const uint SWP_SHOWWINDOW = 0x0040;

    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW")]
    private static extern IntPtr GetWindowLongPtr(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtrW")]
    private static extern IntPtr SetWindowLongPtr(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int x, int y, int cx, int cy, uint uFlags);

    /// <summary>Activa o desactiva estilos extendidos (<c>WS_EX_*</c>) de una ventana.</summary>
    public static void SetExStyle(IntPtr hwnd, long style, bool on)
    {
        if (hwnd == IntPtr.Zero) return;
        var current = GetWindowLongPtr(hwnd, GWL_EXSTYLE).ToInt64();
        var updated = on ? current | style : current & ~style;
        if (updated != current) SetWindowLongPtr(hwnd, GWL_EXSTYLE, new IntPtr(updated));
    }

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    /// <summary>Barra de título oscura (Windows 10 2004 y posteriores). Si no se puede, se queda clara: no es grave.</summary>
    public static void UseDarkTitleBar(IntPtr hwnd)
    {
        var on = 1;
        try
        {
            DwmSetWindowAttribute(hwnd, 20 /* DWMWA_USE_IMMERSIVE_DARK_MODE */, ref on, sizeof(int));
        }
        catch (Exception ex) when (ex is DllNotFoundException or EntryPointNotFoundException)
        {
        }
    }

    // ---- Barra de tareas y monitor ----

    [StructLayout(LayoutKind.Sequential)]
    public struct RECT
    {
        public int Left, Top, Right, Bottom;
        public readonly int Width => Right - Left;
        public readonly int Height => Bottom - Top;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct APPBARDATA
    {
        public uint cbSize;
        public IntPtr hWnd;
        public uint uCallbackMessage;
        public uint uEdge;
        public RECT rc;
        public IntPtr lParam;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct MONITORINFO
    {
        public uint cbSize;
        public RECT rcMonitor;
        public RECT rcWork;
        public uint dwFlags;
    }

    [DllImport("shell32.dll")]
    private static extern IntPtr SHAppBarMessage(uint dwMessage, ref APPBARDATA pData);

    [DllImport("user32.dll")]
    private static extern IntPtr MonitorFromRect(ref RECT lprc, uint dwFlags);

    [DllImport("user32.dll")]
    private static extern IntPtr MonitorFromPoint(POINT pt, uint dwFlags);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetMonitorInfo(IntPtr hMonitor, ref MONITORINFO lpmi);

    [DllImport("shcore.dll")]
    private static extern int GetDpiForMonitor(IntPtr hmonitor, int dpiType, out uint dpiX, out uint dpiY);

    [StructLayout(LayoutKind.Sequential)]
    public struct POINT
    {
        public int X, Y;
    }

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool GetCursorPos(out POINT point);

    private const uint ABM_GETTASKBARPOS = 5;
    private const uint MONITOR_DEFAULTTONEAREST = 2;

    public enum TaskbarEdge { Left = 0, Top = 1, Right = 2, Bottom = 3 }

    /// <summary>Área de trabajo (píxeles) del monitor donde está la bandeja, su escala de DPI y el lado de la barra de tareas.</summary>
    public readonly record struct TrayArea(RECT Work, double Scale, TaskbarEdge Edge);

    public static TrayArea GetTrayArea()
    {
        var data = new APPBARDATA { cbSize = (uint)Marshal.SizeOf<APPBARDATA>() };
        var edge = TaskbarEdge.Bottom;
        IntPtr monitor;
        if (SHAppBarMessage(ABM_GETTASKBARPOS, ref data) != IntPtr.Zero)
        {
            edge = (TaskbarEdge)data.uEdge;
            var rc = data.rc;
            monitor = MonitorFromRect(ref rc, MONITOR_DEFAULTTONEAREST);
        }
        else
        {
            monitor = MonitorFromPoint(new POINT(), MONITOR_DEFAULTTONEAREST); // monitor principal
        }

        var info = new MONITORINFO { cbSize = (uint)Marshal.SizeOf<MONITORINFO>() };
        if (!GetMonitorInfo(monitor, ref info))
            info.rcWork = new RECT { Left = 0, Top = 0, Right = 1280, Bottom = 720 };
        var scale = GetDpiForMonitor(monitor, 0, out var dpiX, out _) == 0 && dpiX > 0 ? dpiX / 96.0 : 1.0;
        return new TrayArea(info.rcWork, scale, edge);
    }

    // ---- Hook de teclado de bajo nivel (solo mientras el campo de atajos tiene el foco) ----

    public delegate IntPtr HookProc(int nCode, IntPtr wParam, IntPtr lParam);

    public const int WH_KEYBOARD_LL = 13;
    public const int WM_KEYDOWN = 0x0100;
    public const int WM_KEYUP = 0x0101;
    public const int WM_SYSKEYDOWN = 0x0104;
    public const int WM_SYSKEYUP = 0x0105;

    [StructLayout(LayoutKind.Sequential)]
    public struct KBDLLHOOKSTRUCT
    {
        public uint vkCode;
        public uint scanCode;
        public uint flags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    public static extern IntPtr SetWindowsHookEx(int idHook, HookProc lpfn, IntPtr hMod, uint dwThreadId);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool UnhookWindowsHookEx(IntPtr hhk);

    [DllImport("user32.dll")]
    public static extern IntPtr CallNextHookEx(IntPtr hhk, int nCode, IntPtr wParam, IntPtr lParam);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    public static extern IntPtr GetModuleHandle(string? lpModuleName);
}
