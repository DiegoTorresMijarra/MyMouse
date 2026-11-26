using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Threading;
using MyMouse.HostCore.Gestures;
using MyMouse.HostCore.Shapes;
using MyMouse.Protocol;

namespace MyMouse.Host.Ui;

internal enum EditorTab
{
    Gestures,
    Widgets,
}

/// <summary>
/// El editor de gestos y widgets (docs/spec-v4-toolbox.md §4): ventana normal con dos pestañas. Reemplaza a editar
/// <c>gestures.json</c> en el Bloc de notas; guarda el fichero con formato limpio (con una copia <c>.bak</c> la primera vez) y
/// no lo pisa si tiene errores de sintaxis.
/// </summary>
internal sealed class EditorWindow : Window
{
    private readonly HostApp _app;
    private readonly Button _gesturesTab = Theme.FlatButton("Gestos");
    private readonly Button _widgetsTab = Theme.FlatButton("Widgets");
    private readonly Border _body = new();
    private GesturesPane? _gestures;
    private WidgetsPane? _widgets;
    private GestureConfig _config = GestureConfig.Empty;

    public EditorWindow(HostApp app)
    {
        _app = app;
        Title = "MyMouse · Gestos y widgets";
        Width = 940;
        Height = 660;
        MinWidth = 760;
        MinHeight = 520;
        Background = Theme.Bg;
        FontFamily = Theme.Font;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        UseLayoutRounding = true;
        SourceInitialized += (_, _) => NativeUi.UseDarkTitleBar(new WindowInteropHelper(this).Handle);

        _gesturesTab.Click += (_, _) => ShowTab(EditorTab.Gestures);
        _widgetsTab.Click += (_, _) => ShowTab(EditorTab.Widgets);
        var file = Theme.FlatButton("⋯");
        file.ToolTip = "Más";
        file.Click += (_, _) => MiniMenu.Show(file,
        [
            ("Abrir el fichero (gestures.json)", null, OpenFile),
            ("Volver a leer el fichero", null, () => Reload(keepSelection: true)),
        ]);

        var tabs = new DockPanel { Margin = new Thickness(16, 14, 16, 8) };
        DockPanel.SetDock(file, Dock.Right);
        tabs.Children.Add(file);
        var chips = new StackPanel { Orientation = Orientation.Horizontal };
        _gesturesTab.Margin = new Thickness(0, 0, 8, 0);
        chips.Children.Add(_gesturesTab);
        chips.Children.Add(_widgetsTab);
        tabs.Children.Add(chips);

        var root = new DockPanel();
        DockPanel.SetDock(tabs, Dock.Top);
        root.Children.Add(tabs);
        root.Children.Add(_body);
        Content = root;

        Closing += (_, e) =>
        {
            if (!ConfirmDiscard()) e.Cancel = true;
        };
        _app.Shapes.Changed += OnShapesChanged;
        Closed += (_, _) => _app.Shapes.Changed -= OnShapesChanged;
        KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape && Keyboard.FocusedElement is not TextBox) Close();
        };
    }

    public GestureConfig Config => _config;

    private void OnShapesChanged() => Dispatcher.BeginInvoke(() => _gestures?.RefreshList());

    /// <summary>
    /// Lee <c>gestures.json</c>. Con errores de sintaxis lo dice y ofrece abrirlo en el Bloc de notas en vez de pisarlo (devuelve false).
    /// </summary>
    public bool Reload(bool keepSelection = false)
    {
        try
        {
            _config = _app.ConfigStorage.Load();
        }
        catch (FormatException ex)
        {
            var choice = Dialogs.Message(IsLoaded ? this : null, "gestures.json tiene errores",
                ex.Message + "\n\nEl editor no lo toca para no perder lo que hay. ¿Lo abres en el Bloc de notas para arreglarlo?",
                "Abrir en el Bloc de notas", "Cancelar");
            if (choice == 0) OpenFile();
            return false;
        }
        catch (IOException ex)
        {
            Dialogs.Message(IsLoaded ? this : null, "No se pudo leer gestures.json", ex.Message);
            return false;
        }
        _gestures?.SetConfig(_config, keepSelection);
        _widgets?.SetConfig(_config);
        return true;
    }

    /// <summary>Guarda <paramref name="updated"/> en el fichero. false si no se pudo (y ya se ha avisado al usuario).</summary>
    public bool TrySave(GestureConfig updated)
    {
        try
        {
            _app.ConfigStorage.Save(updated);
            _config = updated;
            _app.NotifyConfigSavedByEditor();
            return true;
        }
        catch (FormatException ex)
        {
            var choice = Dialogs.Message(this, "No se ha guardado",
                "El fichero gestures.json tiene errores de sintaxis y no se ha sobrescrito.\n" + ex.Message + "\n\n¿Lo abres en el Bloc de notas?",
                "Abrir en el Bloc de notas", "Cancelar");
            if (choice == 0) OpenFile();
            return false;
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            Dialogs.Message(this, "No se ha guardado", ex.Message);
            return false;
        }
    }

    public void OpenFile()
    {
        try
        {
            Process.Start(new ProcessStartInfo("notepad.exe", $"\"{_app.ConfigStorage.Path}\"") { UseShellExecute = true });
        }
        catch (Exception ex) when (ex is System.ComponentModel.Win32Exception or InvalidOperationException)
        {
            FileLog.Write($"No se pudo abrir el Bloc de notas: {ex.Message}");
        }
    }

    /// <summary>¿Se puede cambiar de forma o de pestaña, o cerrar? Si hay cambios sin guardar, se pregunta.</summary>
    public bool ConfirmDiscard() => (_gestures?.ConfirmDiscard() ?? true) && (_widgets?.ConfirmDiscard() ?? true);

    /// <summary>Muestra el editor en la pestaña y el elemento pedidos.</summary>
    public void Present(EditorTab tab, string? gesture, bool focusAction, bool onlyUnassigned, int? widgetIndex, bool newWidget, bool newShape)
    {
        if (!IsLoaded && _gestures is null && !Reload()) return;
        if (!IsVisible) Show();
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
        Activate();

        if (tab == EditorTab.Gestures)
        {
            if (!ShowTab(EditorTab.Gestures)) return;
            if (onlyUnassigned) _gestures!.SetOnlyUnassigned(true);
            if (gesture is not null) _gestures!.Select(gesture, focusAction);
            if (newShape) _gestures!.NewShape();
        }
        else
        {
            if (!ShowTab(EditorTab.Widgets)) return;
            if (widgetIndex is { } i) _widgets!.Select(i);
            if (newWidget) _widgets!.ShowAddMenu();
        }
    }

    private bool ShowTab(EditorTab tab)
    {
        if (!ConfirmDiscard()) return false;
        _gesturesTab.Background = tab == EditorTab.Gestures ? Theme.Accent : Theme.Hover;
        _widgetsTab.Background = tab == EditorTab.Widgets ? Theme.Accent : Theme.Hover;
        if (tab == EditorTab.Gestures)
        {
            _gestures ??= new GesturesPane(_app, this);
            _gestures.SetConfig(_config, keepSelection: true);
            _body.Child = _gestures;
        }
        else
        {
            _widgets ??= new WidgetsPane(_app, this);
            _widgets.SetConfig(_config);
            _body.Child = _widgets;
        }
        return true;
    }
}

/// <summary>Pestaña Gestos: lista de formas a la izquierda y el detalle de la seleccionada a la derecha.</summary>
internal sealed class GesturesPane : Grid
{
    private readonly HostApp _app;
    private readonly EditorWindow _editor;
    private readonly StackPanel _list = new();
    private readonly CheckBox _onlyUnassigned = Theme.Check("Solo sin acción");
    private readonly ScrollViewer _detailScroll;
    private readonly StackPanel _detail = new() { Margin = new Thickness(20, 4, 20, 20) };
    private readonly TextBlock _title = Theme.Label("", 18, weight: FontWeights.SemiBold);
    private readonly TextBlock _subtitle = Theme.Label("", 12, Theme.Dim);
    private readonly ShapeThumb _bigThumb = new(null, 72) { ShowStart = true, Thickness = 3 };
    private readonly ActionEditor _action;
    private readonly TextBox _help = Theme.Input();
    private readonly CheckBox _pin = Theme.Check("Fijar en la chuleta del móvil");
    private readonly StackPanel _appRules = new();
    private readonly StackPanel _appRulesBox = new();
    private readonly Button _appToggle = Theme.FlatButton("");
    private readonly TextBlock _status = Theme.Label("", 12, Theme.Dim);
    private readonly Button _save = Theme.AccentButton("Guardar");
    private readonly Button _test = Theme.FlatButton("Probar");
    private readonly Button _delete = Theme.FlatButton("Borrar forma", Theme.Hover, Theme.Red);
    private readonly List<string> _pendingNames = [];
    private readonly DispatcherTimer _testTimer = new() { Interval = TimeSpan.FromSeconds(3) };
    private GestureConfig _config = GestureConfig.Empty;
    private string? _selected;
    private bool _dirty;
    private bool _loading;
    private bool _appRulesOpen;

    public GesturesPane(HostApp app, EditorWindow editor)
    {
        _app = app;
        _editor = editor;
        _action = new ActionEditor(editor, app.StartMenu);

        ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(310) });
        ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        // Izquierda: filtro, lista y "+ Nueva forma".
        var left = new DockPanel { Margin = new Thickness(16, 0, 8, 16) };
        _onlyUnassigned.Margin = new Thickness(0, 0, 0, 8);
        _onlyUnassigned.Click += (_, _) => RefreshList();
        DockPanel.SetDock(_onlyUnassigned, Dock.Top);
        left.Children.Add(_onlyUnassigned);
        var add = Theme.AccentButton("+ Nueva forma");
        add.Margin = new Thickness(0, 8, 0, 0);
        add.Click += (_, _) => NewShape();
        DockPanel.SetDock(add, Dock.Bottom);
        left.Children.Add(add);
        left.Children.Add(new Border
        {
            Background = Theme.CardBg,
            CornerRadius = new CornerRadius(10),
            Child = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Content = _list, Focusable = false },
        });
        SetColumn(left, 0);
        Children.Add(left);

        // Derecha: el detalle.
        var head = new DockPanel();
        DockPanel.SetDock(_bigThumb, Dock.Left);
        _bigThumb.Margin = new Thickness(0, 0, 14, 0);
        head.Children.Add(_bigThumb);
        var titles = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        titles.Children.Add(_title);
        titles.Children.Add(_subtitle);
        head.Children.Add(titles);
        _detail.Children.Add(head);

        _detail.Children.Add(Section("Acción"));
        _detail.Children.Add(_action);
        _detail.Children.Add(Section("Texto de ayuda (chuleta del móvil y gesto en vivo)"));
        _detail.Children.Add(_help);
        _pin.Margin = new Thickness(0, 12, 0, 0);
        _detail.Children.Add(_pin);

        _appToggle.HorizontalAlignment = HorizontalAlignment.Left;
        _appToggle.Margin = new Thickness(0, 16, 0, 0);
        _appToggle.Click += (_, _) =>
        {
            _appRulesOpen = !_appRulesOpen;
            UpdateAppRulesVisibility();
        };
        _detail.Children.Add(_appToggle);
        var addRule = Theme.FlatButton("+ Añadir regla");
        addRule.HorizontalAlignment = HorizontalAlignment.Left;
        addRule.Click += (_, _) => AddAppRule();
        _appRulesBox.Margin = new Thickness(0, 8, 0, 0);
        _appRulesBox.Children.Add(_appRules);
        _appRulesBox.Children.Add(addRule);
        _detail.Children.Add(_appRulesBox);

        _test.Click += (_, _) => Test();
        _save.Click += (_, _) => Save();
        _delete.Click += async (_, _) => await DeleteAsync();
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 20, 0, 0) };
        _save.Margin = new Thickness(0, 0, 8, 0);
        _test.Margin = new Thickness(0, 0, 8, 0);
        buttons.Children.Add(_save);
        buttons.Children.Add(_test);
        buttons.Children.Add(_delete);
        _detail.Children.Add(buttons);
        _status.Margin = new Thickness(0, 10, 0, 0);
        _status.TextWrapping = TextWrapping.Wrap;
        _status.TextTrimming = TextTrimming.None;
        _detail.Children.Add(_status);

        _detailScroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Content = _detail, Focusable = false };
        SetColumn(_detailScroll, 1);
        Children.Add(_detailScroll);

        _action.Changed += MarkDirty;
        _help.TextChanged += (_, _) => MarkDirty();
        _pin.Click += (_, _) => MarkDirty();
        _testTimer.Tick += (_, _) =>
        {
            _testTimer.Stop();
            RunTest();
        };
        ShowDetail(null);
    }

    private static TextBlock Section(string text)
    {
        var t = Theme.Label(text, 12, Theme.Dim, FontWeights.SemiBold);
        t.Margin = new Thickness(0, 18, 0, 6);
        return t;
    }

    public void SetConfig(GestureConfig config, bool keepSelection)
    {
        _config = config;
        RefreshList();
        if (keepSelection && _selected is not null) LoadSelected();
        else if (_selected is null) ShowDetail(null);
    }

    public void SetOnlyUnassigned(bool on)
    {
        _onlyUnassigned.IsChecked = on;
        RefreshList();
    }

    // ---- Lista ----

    private IEnumerable<string> AllNames()
    {
        var names = new List<string>(ShapeCatalog.Ids);
        foreach (var n in _app.Shapes.Shapes.Select(s => s.Name).Concat(_pendingNames)
                     .Concat(_config.Bindings.Select(b => b.Gesture).Where(g => g.StartsWith(ShapeCatalog.CustomPrefix, StringComparison.Ordinal))))
            if (!names.Contains(n)) names.Add(n);
        return names;
    }

    public void RefreshList()
    {
        _list.Children.Clear();
        foreach (var name in AllNames())
        {
            var hasAction = _config.HasAction(name);
            if (_onlyUnassigned.IsChecked == true && hasAction) continue;
            _list.Children.Add(BuildRow(name, hasAction));
        }
        if (_list.Children.Count == 0) _list.Children.Add(new TextBlock { Text = "Todas las formas tienen acción.", Foreground = Theme.Dim, Margin = new Thickness(12), FontFamily = Theme.Font });
    }

    private UIElement BuildRow(string name, bool hasAction)
    {
        var (general, apps) = _config.RulesOf(name);
        var binding = general ?? apps.FirstOrDefault();
        var texts = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0, 0, 0) };
        texts.Children.Add(Theme.Label(ShapeCatalog.Label(name), 13));
        texts.Children.Add(hasAction
            ? Theme.Label(binding!.HelpText, 11, Theme.Dim)
            : Theme.Label("Sin acción", 11, Theme.Amber));
        var row = new DockPanel();
        var thumb = new ShapeThumb(_app.ShapePoints(name), 34) { VerticalAlignment = VerticalAlignment.Center, Stroke = hasAction ? Theme.Text : Theme.Dim };
        DockPanel.SetDock(thumb, Dock.Left);
        row.Children.Add(thumb);
        row.Children.Add(texts);
        var button = CardChrome.Row(row, () => Select(name, focusAction: false));
        if (name == _selected) button.Background = Theme.Hover;
        return button;
    }

    // ---- Selección y carga ----

    public bool ConfirmDiscard()
    {
        if (!_dirty || _selected is null) return true;
        var choice = Dialogs.Message(_editor, "Cambios sin guardar",
            $"Has cambiado «{ShapeCatalog.Label(_selected)}» y no lo has guardado.", "Guardar", "Descartar", "Seguir editando");
        switch (choice)
        {
            case 0:
                return Save();
            case 1:
                _dirty = false;
                return true;
            default:
                return false;
        }
    }

    public void Select(string name, bool focusAction)
    {
        if (name != _selected && !ConfirmDiscard()) return;
        _selected = name;
        RefreshList();
        LoadSelected();
        if (focusAction) _action.FocusFirst();
    }

    private void LoadSelected()
    {
        if (_selected is not { } name)
        {
            ShowDetail(null);
            return;
        }
        _loading = true;
        try
        {
            ShowDetail(name);
            var (general, apps) = _config.RulesOf(name);
            _action.SetAction(general?.Action);
            _help.Text = general?.Help ?? "";
            _pin.IsChecked = _config.Bindings.Any(b => b.Gesture == name && b.Pinned);
            _appRules.Children.Clear();
            foreach (var rule in apps) _appRules.Children.Add(BuildAppRule(rule));
            _appRulesOpen = apps.Count > 0 && _appRulesOpen;
            UpdateAppRulesVisibility();
            _status.Text = "";
            _dirty = false;
        }
        finally
        {
            _loading = false;
        }
    }

    private void ShowDetail(string? name)
    {
        var visible = name is not null ? Visibility.Visible : Visibility.Collapsed;
        foreach (UIElement child in _detail.Children) child.Visibility = visible;
        if (name is null)
        {
            _title.Text = "";
            return;
        }
        _title.Text = ShapeCatalog.Label(name);
        _subtitle.Text = name + (ShapeCatalog.ById.ContainsKey(name) ? "" : " · forma propia del móvil");
        _bigThumb.Points = _app.ShapePoints(name);
        _delete.Visibility = name.StartsWith(ShapeCatalog.CustomPrefix, StringComparison.Ordinal) ? Visibility.Visible : Visibility.Collapsed;
    }

    private void MarkDirty()
    {
        if (_loading) return;
        _dirty = true;
        _status.Text = "";
    }

    // ---- Reglas por app ----

    private void UpdateAppRulesVisibility()
    {
        var count = _appRules.Children.OfType<AppRuleRow>().Count();
        _appToggle.Content = (_appRulesOpen ? "▾" : "▸") + $" Reglas por app (avanzado){(count > 0 ? $" · {count}" : "")}";
        _appRulesBox.Visibility = _appRulesOpen ? Visibility.Visible : Visibility.Collapsed;
    }

    private AppRuleRow BuildAppRule(GestureBinding? rule)
    {
        var row = new AppRuleRow(_editor, _app, rule);
        row.Changed += MarkDirty;
        row.Removed += () =>
        {
            _appRules.Children.Remove(row);
            MarkDirty();
            UpdateAppRulesVisibility();
        };
        return row;
    }

    private void AddAppRule()
    {
        _appRules.Children.Add(BuildAppRule(null));
        MarkDirty();
        UpdateAppRulesVisibility();
    }

    // ---- Guardar, probar, borrar, crear ----

    private bool Save()
    {
        if (_selected is not { } name) return true;
        try
        {
            var rules = new List<GestureBinding>();
            if (_action.GetAction() is { } action)
                rules.Add(new GestureBinding(name, action, string.IsNullOrWhiteSpace(_help.Text) ? null : _help.Text.Trim(), _pin.IsChecked == true, null));
            foreach (var row in _appRules.Children.OfType<AppRuleRow>()) rules.Add(row.GetBinding(name));
            if (!_editor.TrySave(_config.WithRules(name, rules))) return false;
        }
        catch (FormatException ex)
        {
            _status.Foreground = Theme.Red;
            _status.Text = ex.Message;
            return false;
        }
        _config = _editor.Config;
        _dirty = false;
        _status.Foreground = Theme.Green;
        _status.Text = "Guardado. El PC y el móvil ya usan la nueva acción.";
        RefreshList();
        return true;
    }

    private void Test()
    {
        try
        {
            var action = _action.GetAction();
            if (action is null)
            {
                _status.Foreground = Theme.Dim;
                _status.Text = "No hay acción que probar.";
                return;
            }
            if (action is KeysAction or TextAction)
            {
                // Las teclas irían a esta misma ventana: se da tiempo para pasar a la app donde probarlas.
                _status.Foreground = Theme.Amber;
                _status.Text = "Se ejecutará en 3 segundos: cambia a la ventana donde quieras probarlo.";
                _testTimer.Stop();
                _testTimer.Start();
                return;
            }
            RunTest();
        }
        catch (FormatException ex)
        {
            _status.Foreground = Theme.Red;
            _status.Text = ex.Message;
        }
    }

    private void RunTest()
    {
        try
        {
            if (_action.GetAction() is not { } action) return;
            _app.Executor.Execute(action);
            _status.Foreground = Theme.Green;
            _status.Text = "Ejecutada.";
        }
        catch (Exception ex)
        {
            _status.Foreground = Theme.Red;
            _status.Text = "No se pudo ejecutar: " + ex.Message;
        }
    }

    private async Task DeleteAsync()
    {
        if (_selected is not { } name || !name.StartsWith(ShapeCatalog.CustomPrefix, StringComparison.Ordinal)) return;
        if (Dialogs.Message(_editor, $"Borrar «{ShapeCatalog.Label(name)}»",
                "Se borra del móvil y se quita su acción de gestures.json. No se puede deshacer.", "Borrar", "Cancelar") != 0) return;
        if (!_app.Shapes.CanEdit && _app.Shapes.Contains(name))
        {
            Dialogs.Message(_editor, "Móvil desconectado", "Conecta el móvil por USB para borrar la forma: vive en el móvil.");
            return;
        }

        var result = _app.Shapes.Contains(name) ? await _app.Shapes.DeleteAsync(name) : new ShapeResultMessage(name, ShapeStatus.NotFound, "");
        if (result is null)
        {
            _status.Foreground = Theme.Red;
            _status.Text = "El móvil no ha contestado. ¿Sigue conectado por USB?";
            return;
        }
        _dirty = false;
        _pendingNames.Remove(name);
        if (_editor.TrySave(_editor.Config.WithoutGesture(name)))
        {
            _config = _editor.Config;
            _selected = null;
            RefreshList();
            ShowDetail(null);
        }
    }

    /// <summary>"+ Nueva forma": dibujarla con el ratón o el touchpad y mandarla al móvil.</summary>
    public void NewShape()
    {
        if (!ConfirmDiscard()) return;
        var window = new DrawShapeWindow(_app, _editor);
        window.ShowDialog();
        if (window.CreatedName is not { } name) return;
        if (!_pendingNames.Contains(name) && !_app.Shapes.Contains(name)) _pendingNames.Add(name);
        Select(name, focusAction: true);
    }
}

/// <summary>Una regla "en <c>proceso.exe</c> → otra acción".</summary>
internal sealed class AppRuleRow : Border
{
    private readonly TextBox _process = Theme.Input();
    private readonly TextBox _help = Theme.Input();
    private readonly ActionEditor _action;
    private readonly bool _pinned;

    public AppRuleRow(Window owner, HostApp app, GestureBinding? rule)
    {
        _action = new ActionEditor(owner, app.StartMenu);
        _pinned = rule?.Pinned ?? false;
        Background = Theme.CardBg;
        CornerRadius = new CornerRadius(10);
        Padding = new Thickness(12);
        Margin = new Thickness(0, 0, 0, 8);

        var panel = new StackPanel();
        var top = new DockPanel();
        var remove = Theme.FlatButton("Quitar", Theme.Hover, Theme.Red);
        remove.Margin = new Thickness(8, 0, 0, 0);
        remove.Click += (_, _) => Removed?.Invoke();
        DockPanel.SetDock(remove, Dock.Right);
        top.Children.Add(remove);
        var label = Theme.Label("En", 13, Theme.Dim);
        label.VerticalAlignment = VerticalAlignment.Center;
        label.Margin = new Thickness(0, 0, 8, 0);
        DockPanel.SetDock(label, Dock.Left);
        top.Children.Add(label);
        _process.Text = rule?.App ?? "";
        _process.ToolTip = "Nombre del proceso, p. ej. chrome.exe";
        top.Children.Add(_process);
        panel.Children.Add(top);
        var helpLabel = Theme.Label("Texto de ayuda (opcional)", 12, Theme.Dim);
        helpLabel.Margin = new Thickness(0, 10, 0, 4);
        panel.Children.Add(helpLabel);
        _help.Text = rule?.Help ?? "";
        panel.Children.Add(_help);
        var actionLabel = Theme.Label("→ hace", 12, Theme.Dim);
        actionLabel.Margin = new Thickness(0, 10, 0, 0);
        panel.Children.Add(actionLabel);
        _action.SetAction(rule?.Action);
        panel.Children.Add(_action);
        Child = panel;

        _process.TextChanged += (_, _) => Changed?.Invoke();
        _help.TextChanged += (_, _) => Changed?.Invoke();
        _action.Changed += () => Changed?.Invoke();
    }

    public event Action? Changed;

    public event Action? Removed;

    public GestureBinding GetBinding(string gesture)
    {
        var app = _process.Text.Trim();
        if (app.Length == 0) throw new FormatException("Una regla por app necesita el nombre del proceso (p. ej. chrome.exe).");
        var action = _action.GetAction() ?? throw new FormatException($"La regla de {app} necesita una acción (o quítala).");
        return new GestureBinding(gesture, action, string.IsNullOrWhiteSpace(_help.Text) ? null : _help.Text.Trim(), _pinned, app);
    }
}
