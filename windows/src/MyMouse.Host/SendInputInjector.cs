using System.Runtime.InteropServices;
using MyMouse.HostCore;
using MyMouse.Protocol;

namespace MyMouse.Host;

/// <summary>
/// Inyección mediante Win32 SendInput.
/// Nota: por UIPI no afecta a ventanas que corren como administrador salvo que este proceso también lo esté.
/// </summary>
internal sealed class SendInputInjector : IInputInjector
{
    public void MoveRelative(int dx, int dy) => Send(dx, dy, 0, MOUSEEVENTF_MOVE);

    public void SetButton(MouseButton button, bool pressed)
    {
        var flags = (button, pressed) switch
        {
            (MouseButton.Left, true) => MOUSEEVENTF_LEFTDOWN,
            (MouseButton.Left, false) => MOUSEEVENTF_LEFTUP,
            (MouseButton.Right, true) => MOUSEEVENTF_RIGHTDOWN,
            (MouseButton.Right, false) => MOUSEEVENTF_RIGHTUP,
            (MouseButton.Middle, true) => MOUSEEVENTF_MIDDLEDOWN,
            (MouseButton.Middle, false) => MOUSEEVENTF_MIDDLEUP,
            _ => throw new ArgumentOutOfRangeException(nameof(button)),
        };
        Send(0, 0, 0, flags);
    }

    public void Scroll(int vertical, int horizontal)
    {
        if (vertical != 0) Send(0, 0, vertical, MOUSEEVENTF_WHEEL);
        if (horizontal != 0) Send(0, 0, horizontal, MOUSEEVENTF_HWHEEL);
    }

    private static void Send(int dx, int dy, int mouseData, uint flags)
    {
        var input = new INPUT
        {
            type = INPUT_MOUSE,
            u = new InputUnion
            {
                mi = new MOUSEINPUT { dx = dx, dy = dy, mouseData = unchecked((uint)mouseData), dwFlags = flags },
            },
        };

        // Si falla (p. ej. escritorio seguro activo) se descarta el evento: no tiene sentido cortar la sesión.
        _ = SendInput(1, [input], Marshal.SizeOf<INPUT>());
    }

    private const uint INPUT_MOUSE = 0;
    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800;
    private const uint MOUSEEVENTF_HWHEEL = 0x1000;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public InputUnion u;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki; // usado en v1 (acciones de teclado); fija el tamaño correcto de la unión
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
}
