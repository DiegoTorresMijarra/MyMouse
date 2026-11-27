using MyMouse.HostCore.Keyboard;
using MyMouse.Protocol;
using static MyMouse.Host.Native;

namespace MyMouse.Host;

/// <summary>Luces del teclado con GetKeyState y distribución de la ventana en primer plano con GetKeyboardLayout (docs/spec-v6-teclado.md §5.4 y §6).</summary>
internal sealed class WindowsKeyboardState : IKeyboardState
{
    public byte ReadLeds()
    {
        byte bits = 0;
        // El bit bajo de GetKeyState es el estado de la tecla de bloqueo (encendida o apagada).
        if ((GetKeyState(VK_CAPITAL) & 1) != 0) bits |= KeyboardLedsMessage.Caps;
        if ((GetKeyState(VK_NUMLOCK) & 1) != 0) bits |= KeyboardLedsMessage.Num;
        if ((GetKeyState(VK_SCROLL) & 1) != 0) bits |= KeyboardLedsMessage.Scroll;
        return bits;
    }

    public string ReadLayoutId()
    {
        var hwnd = GetForegroundWindow();
        if (hwnd == IntPtr.Zero) return "";
        var thread = GetWindowThreadProcessId(hwnd, out _);
        var hkl = GetKeyboardLayout(thread).ToInt64();
        // La palabra baja del HKL es el idioma de la distribución (0x040A = Español, 0x0409 = Inglés EE. UU.).
        return KeyboardLayoutIds.FromLangId((ushort)(hkl & 0xFFFF));
    }
}
