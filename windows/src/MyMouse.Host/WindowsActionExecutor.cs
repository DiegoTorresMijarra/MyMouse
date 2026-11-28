using System.ComponentModel;
using System.Diagnostics;
using MyMouse.HostCore.Gestures;
using static MyMouse.Host.Native;

namespace MyMouse.Host;

/// <summary>Ejecuta las acciones del modo gestos en Windows (docs/spec-v1-gestos.md §3).</summary>
internal sealed class WindowsActionExecutor(Action togglePause, Action openPhoneKeyboard) : IActionExecutor
{
    public void Execute(GestureAction action)
    {
        switch (action)
        {
            case KeysAction k:
                foreach (var stroke in k.Strokes) Press(stroke);
                break;
            case MediaAction m:
                Press(m.Stroke);
                break;
            case TextAction t:
                Type(t.Text);
                break;
            case LaunchAction l:
                Launch(l);
                break;
            case MyMouseAction { Command: MyMouseCommand.Pause }:
                togglePause();
                break;
            case MyMouseAction { Command: MyMouseCommand.Lock }:
                if (!LockWorkStation()) throw new Win32Exception();
                break;
            case MyMouseAction { Command: MyMouseCommand.Keyboard }:
                openPhoneKeyboard();
                break;
            default:
                throw new NotSupportedException(action.GetType().Name);
        }
    }

    /// <summary>Modificadores abajo, tecla, y todo arriba en orden inverso, en una sola llamada.</summary>
    private static void Press(KeyStroke stroke)
    {
        var mods = new List<ushort>();
        foreach (var m in new[] { Modifiers.Ctrl, Modifiers.Shift, Modifiers.Alt, Modifiers.Win })
            if (stroke.Modifiers.HasFlag(m)) mods.Add(KeyChord.ModifierKey(m));

        var inputs = new List<INPUT>();
        inputs.AddRange(mods.Select(vk => Key(vk, up: false, KeyChord.IsExtended(vk))));
        inputs.Add(Key(stroke.VirtualKey, up: false, KeyChord.IsExtended(stroke.VirtualKey)));
        inputs.Add(Key(stroke.VirtualKey, up: true, KeyChord.IsExtended(stroke.VirtualKey)));
        inputs.AddRange(Enumerable.Reverse(mods).Select(vk => Key(vk, up: true, KeyChord.IsExtended(vk))));
        if (Send([.. inputs]) != inputs.Count)
            throw new InvalidOperationException(Loc.T("wa_keys_rejected"));
    }

    /// <summary>Texto como caracteres Unicode: no depende de la distribución del teclado. "\n" = Intro.</summary>
    private static void Type(string text)
    {
        var inputs = new List<INPUT>();
        foreach (var c in text.Replace("\r\n", "\n"))
        {
            if (c == '\n')
            {
                inputs.Add(Key(0x0D, up: false));
                inputs.Add(Key(0x0D, up: true));
            }
            else
            {
                inputs.Add(Unicode(c, up: false));
                inputs.Add(Unicode(c, up: true));
            }
        }
        if (Send([.. inputs]) != inputs.Count)
            throw new InvalidOperationException(Loc.T("wa_text_rejected"));
    }

    private static void Launch(LaunchAction l)
    {
        if (l.FocusIfRunning && TryFocus(l.Target)) return;
        var info = new ProcessStartInfo(l.Target) { UseShellExecute = true };
        if (!string.IsNullOrWhiteSpace(l.Arguments)) info.Arguments = l.Arguments;
        Process.Start(info);
    }

    /// <summary>Trae al frente una ventana del programa si ya está abierto. Windows puede impedirlo (docs/spec-v1-gestos.md §7).</summary>
    private static bool TryFocus(string target)
    {
        if (target.Contains("://", StringComparison.Ordinal) || target.EndsWith(':')) return false;
        var name = Path.GetFileNameWithoutExtension(target);
        if (string.IsNullOrEmpty(name)) return false;

        foreach (var p in Process.GetProcessesByName(name))
        {
            using (p)
            {
                var hwnd = p.MainWindowHandle;
                if (hwnd == IntPtr.Zero) continue;
                if (IsIconic(hwnd)) ShowWindow(hwnd, SW_RESTORE);
                SetForegroundWindow(hwnd);
                return true;
            }
        }
        return false;
    }

    /// <summary>Nombre del proceso de la ventana activa (sin ".exe"), o null.</summary>
    public static string? ForegroundProcessName()
    {
        var hwnd = GetForegroundWindow();
        if (hwnd == IntPtr.Zero) return null;
        GetWindowThreadProcessId(hwnd, out var pid);
        try
        {
            using var p = Process.GetProcessById((int)pid);
            return p.ProcessName;
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException or Win32Exception)
        {
            return null;
        }
    }
}
