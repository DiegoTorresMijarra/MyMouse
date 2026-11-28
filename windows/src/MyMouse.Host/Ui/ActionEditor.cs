using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using MyMouse.HostCore.Apps;
using MyMouse.HostCore.Gestures;

namespace MyMouse.Host.Ui;

/// <summary>
/// Campo de atajo (docs/spec-v4-toolbox.md §4.1): al tener el foco <b>graba</b> la combinación que se pulsa, incluida la tecla Win,
/// con un hook de teclado de bajo nivel que solo existe mientras el campo está activo. Con el botón ✎ se puede escribir a mano.
/// </summary>
internal sealed class ChordBox : DockPanel
{
    private readonly TextBox _box;
    private readonly Button _manual;
    private readonly ChordRecorder _recorder = new();
    private readonly HashSet<ushort> _down = [];
    private readonly NativeUi.HookProc _proc;
    private IntPtr _hook;
    private bool _typing;

    public ChordBox()
    {
        _proc = OnHook; // se guarda en un campo: si no, el recolector libera el delegado y el hook falla
        _box = Theme.Input();
        _box.IsReadOnly = true;
        _box.IsReadOnlyCaretVisible = false;
        _box.Cursor = Cursors.Arrow;
        _box.GotKeyboardFocus += (_, _) => StartRecording();
        _box.LostKeyboardFocus += (_, _) => StopRecording();
        _box.TextChanged += (_, _) => Changed?.Invoke();
        _box.Unloaded += (_, _) => StopRecording();

        _manual = Theme.FlatButton("✎");
        _manual.ToolTip = Loc.T("ae_type_by_hand");
        _manual.Margin = new Thickness(6, 0, 0, 0);
        _manual.Click += (_, _) => ToggleManual();
        SetDock(_manual, Dock.Right);
        Children.Add(_manual);
        Children.Add(_box);
        ShowHint();
    }

    public event Action? Changed;

    public string Text
    {
        get => _box.Text;
        set
        {
            _recorder.Reset();
            _box.Text = value;
            ShowHint();
        }
    }

    public new bool Focus() => _box.Focus();

    private void ShowHint()
    {
        // Sin texto, el cuadro no tiene sitio para una pista: se usa el tooltip.
        _box.ToolTip = _typing ? Loc.T("ae_type_shortcut_hint") : Loc.T("ae_click_press_combo");
    }

    private void ToggleManual()
    {
        _typing = !_typing;
        _box.IsReadOnly = !_typing;
        _box.IsReadOnlyCaretVisible = _typing;
        _box.Cursor = _typing ? Cursors.IBeam : Cursors.Arrow;
        _manual.Background = _typing ? Theme.AccentGradient : Theme.Hover;
        if (_typing) StopRecording();
        ShowHint();
        _box.Focus();
    }

    private void StartRecording()
    {
        if (_typing || _hook != IntPtr.Zero) return;
        _recorder.Reset();
        _down.Clear();
        _hook = NativeUi.SetWindowsHookEx(NativeUi.WH_KEYBOARD_LL, _proc, NativeUi.GetModuleHandle(null), 0);
        if (_hook == IntPtr.Zero)
        {
            FileLog.Write($"No se pudo instalar el hook de teclado: error {Marshal.GetLastWin32Error()}. Escribe el atajo a mano (✎).");
            return;
        }
        _box.Text = "";
        _box.Background = Theme.CardBg;
    }

    private void StopRecording()
    {
        if (_hook == IntPtr.Zero) return;
        NativeUi.UnhookWindowsHookEx(_hook);
        _hook = IntPtr.Zero;
        _box.Background = Theme.Bg;
    }

    private IntPtr OnHook(int code, IntPtr wParam, IntPtr lParam)
    {
        if (code < 0 || _hook == IntPtr.Zero) return NativeUi.CallNextHookEx(_hook, code, wParam, lParam);
        var message = (int)wParam;
        var vk = (ushort)Marshal.PtrToStructure<NativeUi.KBDLLHOOKSTRUCT>(lParam).vkCode;
        var down = message is NativeUi.WM_KEYDOWN or NativeUi.WM_SYSKEYDOWN;
        var up = message is NativeUi.WM_KEYUP or NativeUi.WM_SYSKEYUP;
        if (!down && !up) return NativeUi.CallNextHookEx(_hook, code, wParam, lParam);

        if (down)
        {
            // Una combinación nueva empieza cuando se han soltado todas las teclas de la anterior.
            if (_recorder.IsComplete && _down.Count == 0) _recorder.Reset();
            _down.Add(vk);
            _recorder.KeyDown(vk);
        }
        else
        {
            _down.Remove(vk);
            _recorder.KeyUp(vk);
        }
        _box.Text = _recorder.Text;
        return (IntPtr)1; // se traga la tecla: Win no abre el menú Inicio ni Alt+Tab cambia de ventana mientras se graba
    }
}

/// <summary>
/// Formulario de una acción (docs/spec-v4-toolbox.md §4.1): selector de tipo y un formulario para cada uno. Lo usan las formas, las
/// reglas por app y los botones del panel de widgets.
/// </summary>
internal sealed class ActionEditor : StackPanel
{
    private readonly IStartMenuApps _startMenu;
    private readonly Window _owner;
    private readonly WrapPanel _kindRow = new();
    private readonly Dictionary<ActionKind, Button> _kindButtons = [];
    private readonly Border _formHost = new() { Margin = new Thickness(0, 10, 0, 0) };
    private readonly Dictionary<ActionKind, FrameworkElement> _forms = [];

    private readonly ChordBox _chord = new();
    private readonly TextBox _target = Theme.Input();
    private readonly TextBox _args = Theme.Input();
    private readonly CheckBox _focus = Theme.Check(Loc.T("ae_bring_to_front"));
    private readonly TextBox _text = Theme.Input(multiline: true);
    private string _mediaKey = "PlayPause";
    private MyMouseCommand _command = MyMouseCommand.Pause;
    private readonly StackPanel _mediaChips = new();
    private readonly StackPanel _commandChips = new();
    private readonly StackPanel _appRows = new();
    private readonly TextBox _search = Theme.Input();
    private IReadOnlyList<InstalledApp>? _apps;
    private bool _loadingApps;
    private ActionKind _kind = ActionKind.None;
    private bool _suspend;

    public ActionEditor(Window owner, IStartMenuApps startMenu)
    {
        _owner = owner;
        _startMenu = startMenu;
        foreach (var kind in Enum.GetValues<ActionKind>())
        {
            var k = kind;
            var b = Theme.FlatButton(ActionText.KindLabel(kind), padding: new Thickness(10, 5, 10, 5));
            b.Margin = new Thickness(0, 0, 6, 6);
            b.Focusable = true; // para poder llevar el foco aquí al abrir el editor desde Asignar
            b.Click += (_, _) => SetKind(k, raise: true);
            _kindButtons[kind] = b;
            _kindRow.Children.Add(b);
        }
        Children.Add(_kindRow);
        Children.Add(_formHost);

        _chord.Changed += Raise;
        _target.TextChanged += (_, _) => Raise();
        _args.TextChanged += (_, _) => Raise();
        _focus.Click += (_, _) => Raise();
        _text.TextChanged += (_, _) => Raise();
        _text.MinHeight = 80;
        _text.VerticalContentAlignment = VerticalAlignment.Top;
        _text.VerticalScrollBarVisibility = ScrollBarVisibility.Auto;
        BuildChoiceChips(_mediaChips, ActionText.MediaChoices.Select(kv => (kv.Value, (Action)(() => { _mediaKey = kv.Key; Raise(); }), (Func<bool>)(() => _mediaKey == kv.Key))));
        BuildChoiceChips(_commandChips,
        [
            (Loc.T("ae_pause_resume"), () => { _command = MyMouseCommand.Pause; Raise(); }, () => _command == MyMouseCommand.Pause),
            (Loc.T("ae_lock_pc"), () => { _command = MyMouseCommand.Lock; Raise(); }, () => _command == MyMouseCommand.Lock),
            (Loc.T("ae_open_phone_keyboard"), () => { _command = MyMouseCommand.Keyboard; Raise(); }, () => _command == MyMouseCommand.Keyboard),
        ]);
        SetKind(ActionKind.None, raise: false);
    }

    /// <summary>El usuario ha cambiado algo.</summary>
    public event Action? Changed;

    public ActionKind Kind => _kind;

    private void Raise()
    {
        if (!_suspend) Changed?.Invoke();
    }

    /// <summary>Pone el foco en el selector de tipo (al abrir el editor desde Asignar).</summary>
    public void FocusFirst()
    {
        BringIntoView();
        _kindButtons[_kind].Focus();
    }

    /// <summary>Carga una acción en el formulario (null = ninguna).</summary>
    public void SetAction(GestureAction? action)
    {
        _suspend = true;
        try
        {
            _chord.Text = "";
            _target.Text = "";
            _args.Text = "";
            _focus.IsChecked = false;
            _text.Text = "";
            switch (action)
            {
                case KeysAction k:
                    _chord.Text = k.Text;
                    break;
                case LaunchAction l:
                    _target.Text = l.Target;
                    _args.Text = l.Arguments ?? "";
                    _focus.IsChecked = l.FocusIfRunning;
                    break;
                case TextAction t:
                    _text.Text = t.Text;
                    break;
                case MediaAction m:
                    _mediaKey = ActionText.MediaChoices.Select(kv => kv.Key).FirstOrDefault(k => string.Equals(k, m.Key, StringComparison.OrdinalIgnoreCase)) ?? m.Key;
                    break;
                case MyMouseAction c:
                    _command = c.Command;
                    break;
            }
            SetKind(ActionText.KindOf(action), raise: false);
            RefreshChips();
        }
        finally
        {
            _suspend = false;
        }
    }

    /// <summary>
    /// La acción que describe el formulario (null para "Ninguna"). Lanza <see cref="FormatException"/> con un texto para el usuario
    /// si falta algo o el atajo no se entiende.
    /// </summary>
    public GestureAction? GetAction() => _kind switch
    {
        ActionKind.None => null,
        ActionKind.Keys => new KeysAction(KeyChord.Parse(_chord.Text)),
        ActionKind.Launch => !string.IsNullOrWhiteSpace(_target.Text)
            ? new LaunchAction(_target.Text.Trim(), string.IsNullOrWhiteSpace(_args.Text) ? null : _args.Text.Trim(), _focus.IsChecked == true)
            : throw new FormatException(Loc.T("ae_err_launch")),
        ActionKind.Text => !string.IsNullOrEmpty(_text.Text)
            ? new TextAction(_text.Text)
            : throw new FormatException(Loc.T("ae_err_text")),
        ActionKind.Media => new MediaAction(_mediaKey, KeyChord.Parse(_mediaKey)[0]),
        ActionKind.MyMouse => new MyMouseAction(_command),
        _ => null,
    };

    private void SetKind(ActionKind kind, bool raise)
    {
        _kind = kind;
        foreach (var (k, b) in _kindButtons) b.Background = k == kind ? Theme.AccentGradient : Theme.Hover;
        if (!_forms.TryGetValue(kind, out var form)) _forms[kind] = form = BuildForm(kind);
        _formHost.Child = form;
        if (kind == ActionKind.Launch) LoadAppsOnce();
        if (raise) Raise();
    }

    private FrameworkElement BuildForm(ActionKind kind)
    {
        var panel = new StackPanel();
        switch (kind)
        {
            case ActionKind.None:
                panel.Children.Add(Theme.Label(Loc.T("ae_shape_does_nothing"), 12, Theme.Dim));
                break;
            case ActionKind.Keys:
                panel.Children.Add(Theme.Label(Loc.T("ae_press_combo"), 12, Theme.Dim));
                _chord.Margin = new Thickness(0, 4, 0, 0);
                panel.Children.Add(_chord);
                panel.Children.Add(Pad(Theme.Label(Loc.T("ae_keys_help"), 11, Theme.Dim)));
                break;
            case ActionKind.Launch:
                BuildLaunchForm(panel);
                break;
            case ActionKind.Text:
                panel.Children.Add(Theme.Label(Loc.T("ae_text_hint"), 12, Theme.Dim));
                _text.Margin = new Thickness(0, 4, 0, 0);
                panel.Children.Add(_text);
                break;
            case ActionKind.Media:
                panel.Children.Add(_mediaChips);
                break;
            case ActionKind.MyMouse:
                panel.Children.Add(_commandChips);
                break;
        }
        return panel;
    }

    private void BuildLaunchForm(StackPanel panel)
    {
        panel.Children.Add(Theme.Label(Loc.T("ae_launch_hint"), 12, Theme.Dim));
        var targetRow = new DockPanel { Margin = new Thickness(0, 4, 0, 0) };
        var pick = Theme.FlatButton(Loc.T("ae_choose_program"));
        pick.Margin = new Thickness(6, 0, 0, 0);
        pick.Click += (_, _) =>
        {
            var dialog = new Microsoft.Win32.OpenFileDialog { Filter = Loc.T("ae_file_filter"), Title = Loc.T("ae_choose_program_title") };
            if (dialog.ShowDialog(_owner) == true) _target.Text = dialog.FileName;
        };
        DockPanel.SetDock(pick, Dock.Right);
        targetRow.Children.Add(pick);
        targetRow.Children.Add(_target);
        panel.Children.Add(targetRow);

        panel.Children.Add(Pad(Theme.Label(Loc.T("ae_installed_apps"), 12, Theme.Dim), 12));
        _search.Margin = new Thickness(0, 4, 0, 4);
        _search.TextChanged += (_, _) => FillApps();
        panel.Children.Add(_search);
        panel.Children.Add(new Border
        {
            Background = Theme.Bg,
            BorderBrush = Theme.Line,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(6),
            Child = new ScrollViewer { MaxHeight = 160, VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Content = _appRows, Focusable = false },
        });

        panel.Children.Add(Pad(Theme.Label(Loc.T("ae_arguments"), 12, Theme.Dim), 12));
        _args.Margin = new Thickness(0, 4, 0, 0);
        panel.Children.Add(_args);
        _focus.Margin = new Thickness(0, 10, 0, 0);
        panel.Children.Add(_focus);
    }

    private void LoadAppsOnce()
    {
        if (_apps is not null || _loadingApps) return;
        _loadingApps = true;
        _appRows.Children.Add(Theme.Label(Loc.T("ae_searching_apps"), 12, Theme.Dim));
        _ = Task.Run(() =>
        {
            try
            {
                return _startMenu.List();
            }
            catch (Exception ex)
            {
                FileLog.Write($"No se pudo leer el menú Inicio: {ex.Message}");
                return [];
            }
        }).ContinueWith(t => Dispatcher.BeginInvoke(() =>
        {
            _apps = t.Result;
            _loadingApps = false;
            FillApps();
        }));
    }

    private void FillApps()
    {
        _appRows.Children.Clear();
        if (_apps is null) return;
        var shown = AppSearch.Filter(_apps, _search.Text).Take(80).ToList();
        foreach (var app in shown)
        {
            var row = CardChrome.Row(Theme.Label(app.Name, 13), () => _target.Text = app.Path);
            row.Background = Theme.Bg;
            _appRows.Children.Add(row);
        }
        if (shown.Count == 0) _appRows.Children.Add(Pad(Theme.Label(_apps.Count == 0 ? Loc.T("ae_no_apps") : Loc.T("ae_no_match"), 12, Theme.Dim), 8));
    }

    private void BuildChoiceChips(StackPanel host, IEnumerable<(string Text, Action Click, Func<bool> Selected)> choices)
    {
        host.Children.Clear();
        var wrap = new WrapPanel();
        foreach (var (text, click, selected) in choices)
        {
            var b = Theme.FlatButton(text, padding: new Thickness(10, 5, 10, 5));
            b.Margin = new Thickness(0, 0, 6, 6);
            b.Tag = selected;
            b.Click += (_, _) =>
            {
                click();
                RefreshChips();
            };
            wrap.Children.Add(b);
        }
        host.Children.Add(wrap);
        RefreshChips();
    }

    private void RefreshChips()
    {
        foreach (var host in new[] { _mediaChips, _commandChips })
            foreach (var wrap in host.Children.OfType<WrapPanel>())
                foreach (var b in wrap.Children.OfType<Button>())
                    b.Background = b.Tag is Func<bool> selected && selected() ? Theme.AccentGradient : Theme.Hover;
    }

    private static FrameworkElement Pad(FrameworkElement e, double top = 6)
    {
        e.Margin = new Thickness(0, top, 0, 0);
        if (e is TextBlock t)
        {
            t.TextWrapping = TextWrapping.Wrap;
            t.TextTrimming = TextTrimming.None;
        }
        return e;
    }
}
