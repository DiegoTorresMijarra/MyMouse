using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using MyMouse.HostCore.Gestures;
using MyMouse.Protocol;

namespace MyMouse.Host.Ui;

/// <summary>
/// Pestaña Widgets (docs/spec-v4-toolbox.md §4.3): lista ordenable de los widgets del panel del móvil, su detalle y una vista previa
/// del panel (rejilla de 4 columnas). Se guarda en <c>"widgets"</c> de gestures.json.
/// </summary>
internal sealed class WidgetsPane : Grid
{
    private readonly HostApp _app;
    private readonly EditorWindow _editor;
    private readonly StackPanel _list = new();
    private readonly StackPanel _detail = new() { Margin = new Thickness(20, 4, 20, 20) };
    private readonly TextBox _label = Theme.Input();
    private readonly TextBox _icon = Theme.Input();
    private readonly ActionEditor _action;
    private readonly StackPanel _buttonForm = new();
    private readonly TextBlock _info = Theme.Label("", 13, Theme.Dim);
    private readonly TextBlock _status = Theme.Label("", 12, Theme.Dim);
    private readonly UniformGrid _preview = new() { Columns = 4 };
    private readonly Button _save = Theme.AccentButton("Guardar");
    private GestureConfig _config = GestureConfig.Empty;
    private List<WidgetDefinition> _widgets = [];
    private int _selected = -1;
    private bool _dirty;
    private bool _loading;

    public WidgetsPane(HostApp app, EditorWindow editor)
    {
        _app = app;
        _editor = editor;
        _action = new ActionEditor(editor, app.StartMenu);

        ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(310) });
        ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        var left = new DockPanel { Margin = new Thickness(16, 0, 8, 16) };
        var add = Theme.AccentButton("+ Añadir");
        add.Margin = new Thickness(0, 8, 0, 0);
        add.Click += (_, _) => ShowAddMenu();
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

        _detail.Children.Add(_info);
        var labelTitle = Theme.Label("Etiqueta", 12, Theme.Dim, FontWeights.SemiBold);
        labelTitle.Margin = new Thickness(0, 0, 0, 4);
        _buttonForm.Children.Add(labelTitle);
        _buttonForm.Children.Add(_label);
        var iconTitle = Theme.Label("Icono (un emoji o 1–3 letras)", 12, Theme.Dim, FontWeights.SemiBold);
        iconTitle.Margin = new Thickness(0, 14, 0, 4);
        _buttonForm.Children.Add(iconTitle);
        var iconRow = new DockPanel();
        var emoji = Theme.FlatButton("😀 Emojis (Win+.)");
        emoji.Margin = new Thickness(6, 0, 0, 0);
        emoji.Click += (_, _) => OpenEmojiPicker();
        DockPanel.SetDock(emoji, Dock.Right);
        iconRow.Children.Add(emoji);
        iconRow.Children.Add(_icon);
        _buttonForm.Children.Add(iconRow);
        var actionTitle = Theme.Label("Acción", 12, Theme.Dim, FontWeights.SemiBold);
        actionTitle.Margin = new Thickness(0, 14, 0, 6);
        _buttonForm.Children.Add(actionTitle);
        _buttonForm.Children.Add(_action);
        _detail.Children.Add(_buttonForm);

        var previewTitle = Theme.Label("Vista previa del panel del móvil", 12, Theme.Dim, FontWeights.SemiBold);
        previewTitle.Margin = new Thickness(0, 24, 0, 6);
        _detail.Children.Add(previewTitle);
        _detail.Children.Add(new Border
        {
            Background = Theme.CardBg,
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(8),
            MaxWidth = 420,
            HorizontalAlignment = HorizontalAlignment.Left,
            Child = _preview,
        });

        _save.Margin = new Thickness(0, 20, 0, 0);
        _save.HorizontalAlignment = HorizontalAlignment.Left;
        _save.Click += (_, _) => Save();
        _detail.Children.Add(_save);
        _status.Margin = new Thickness(0, 10, 0, 0);
        _status.TextWrapping = TextWrapping.Wrap;
        _status.TextTrimming = TextTrimming.None;
        _detail.Children.Add(_status);

        var scroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Content = _detail, Focusable = false };
        SetColumn(scroll, 1);
        Children.Add(scroll);

        _label.TextChanged += (_, _) => Commit();
        _icon.TextChanged += (_, _) => Commit();
        _action.Changed += Commit;
        Rebuild();
    }

    public void SetConfig(GestureConfig config)
    {
        if (_dirty) return; // no se pisa lo que se está editando
        _config = config;
        _widgets = [.. config.Widgets];
        _selected = _widgets.Count > 0 ? Math.Min(Math.Max(_selected, 0), _widgets.Count - 1) : -1;
        Rebuild();
    }

    public bool ConfirmDiscard()
    {
        if (!_dirty) return true;
        var choice = Dialogs.Message(_editor, "Cambios sin guardar", "Has cambiado los widgets y no los has guardado.", "Guardar", "Descartar", "Seguir editando");
        if (choice == 0) return Save();
        if (choice != 1) return false;
        _dirty = false;
        _widgets = [.. _editor.Config.Widgets];
        return true;
    }

    public void Select(int index)
    {
        if (index < 0 || index >= _widgets.Count) return;
        _selected = index;
        Rebuild();
    }

    public void ShowAddMenu()
    {
        var items = new List<(string, bool?, Action)> { ("Botón", null, () => Add(new ButtonWidget("Nuevo", "▫", new TextAction("hola")))) };
        if (_widgets.All(w => w is not MediaWidget)) items.Add(("Música (lo que suena)", null, () => Add(new MediaWidget())));
        if (_widgets.All(w => w is not VolumeWidget)) items.Add(("Volumen", null, () => Add(new VolumeWidget())));
        MiniMenu.Show(_list.Parent as UIElement ?? this, items);
    }

    private void Add(WidgetDefinition widget)
    {
        if (_widgets.Count >= ProtocolInfo.MaxWidgets)
        {
            _status.Foreground = Theme.Red;
            _status.Text = $"Como máximo {ProtocolInfo.MaxWidgets} widgets.";
            return;
        }
        _widgets.Add(widget);
        _selected = _widgets.Count - 1;
        _dirty = true;
        Rebuild();
    }

    private void Rebuild()
    {
        RebuildList();
        LoadDetail();
        RebuildPreview();
    }

    private void RebuildList()
    {
        _list.Children.Clear();
        for (var i = 0; i < _widgets.Count; i++)
        {
            var index = i;
            var (icon, label) = WidgetsCard.Parts(_widgets[i]);
            var row = new DockPanel();
            var remove = Theme.FlatButton("✕", Theme.CardBg, Theme.Dim, padding: new Thickness(7, 3, 7, 3));
            var down = Theme.FlatButton("↓", Theme.CardBg, Theme.Dim, padding: new Thickness(7, 3, 7, 3));
            var up = Theme.FlatButton("↑", Theme.CardBg, Theme.Dim, padding: new Thickness(7, 3, 7, 3));
            remove.Click += (_, _) => Remove(index);
            down.Click += (_, _) => Move(index, 1);
            up.Click += (_, _) => Move(index, -1);
            up.IsEnabled = i > 0;
            down.IsEnabled = i < _widgets.Count - 1;
            foreach (var b in new[] { remove, down, up })
            {
                DockPanel.SetDock(b, Dock.Right);
                row.Children.Add(b);
            }
            var glyph = Theme.Label(icon, 16);
            glyph.Width = 30;
            glyph.VerticalAlignment = VerticalAlignment.Center;
            DockPanel.SetDock(glyph, Dock.Left);
            row.Children.Add(glyph);
            var text = Theme.Label(label, 13);
            text.VerticalAlignment = VerticalAlignment.Center;
            row.Children.Add(text);
            var button = CardChrome.Row(row, () => { _selected = index; Rebuild(); });
            if (i == _selected) button.Background = Theme.Hover;
            _list.Children.Add(button);
        }
        if (_widgets.Count == 0)
            _list.Children.Add(new TextBlock { Text = "No hay widgets: el móvil no muestra panel.", Foreground = Theme.Dim, Margin = new Thickness(12), FontFamily = Theme.Font, TextWrapping = TextWrapping.Wrap });
    }

    private void Remove(int index)
    {
        _widgets.RemoveAt(index);
        _selected = Math.Min(_selected, _widgets.Count - 1);
        _dirty = true;
        Rebuild();
    }

    private void Move(int index, int delta)
    {
        var to = index + delta;
        if (to < 0 || to >= _widgets.Count) return;
        (_widgets[index], _widgets[to]) = (_widgets[to], _widgets[index]);
        if (_selected == index) _selected = to;
        _dirty = true;
        Rebuild();
    }

    private void LoadDetail()
    {
        _loading = true;
        try
        {
            var widget = _selected >= 0 && _selected < _widgets.Count ? _widgets[_selected] : null;
            _buttonForm.Visibility = widget is ButtonWidget ? Visibility.Visible : Visibility.Collapsed;
            _info.Visibility = widget is ButtonWidget ? Visibility.Collapsed : Visibility.Visible;
            switch (widget)
            {
                case ButtonWidget b:
                    _label.Text = b.Label;
                    _icon.Text = b.Icon;
                    _action.SetAction(b.Action);
                    break;
                case MediaWidget:
                    _info.Text = "Música: muestra en el móvil lo que suena en el PC, con ⏮ ⏯ ⏭. No tiene más ajustes.";
                    break;
                case VolumeWidget:
                    _info.Text = "Volumen: un control deslizante con silencio para el volumen del PC. No tiene más ajustes.";
                    break;
                default:
                    _info.Text = "Añade un widget con «+ Añadir» o elige uno de la lista.";
                    break;
            }
            _info.TextWrapping = TextWrapping.Wrap;
            _info.TextTrimming = TextTrimming.None;
        }
        finally
        {
            _loading = false;
        }
    }

    /// <summary>Aplica lo escrito al widget seleccionado, si es válido (si no, se mantiene el anterior y se avisa).</summary>
    private void Commit()
    {
        if (_loading || _selected < 0 || _selected >= _widgets.Count || _widgets[_selected] is not ButtonWidget) return;
        try
        {
            var label = _label.Text.Trim();
            if (label.Length == 0) throw new FormatException("El botón necesita una etiqueta.");
            if (label.Length > 40) throw new FormatException("La etiqueta puede tener como mucho 40 caracteres.");
            var icon = _icon.Text.Trim();
            if (System.Text.Encoding.UTF8.GetByteCount(icon) > 8) throw new FormatException("El icono es demasiado largo: un emoji o 1–3 letras.");
            var action = _action.GetAction() ?? throw new FormatException("El botón necesita una acción.");
            _widgets[_selected] = new ButtonWidget(label, icon, action);
            _dirty = true;
            _status.Text = "";
            RebuildList();
            RebuildPreview();
        }
        catch (FormatException ex)
        {
            _status.Foreground = Theme.Amber;
            _status.Text = ex.Message + " (no se aplica hasta que esté bien)";
        }
    }

    private void RebuildPreview()
    {
        _preview.Children.Clear();
        foreach (var widget in _widgets)
        {
            var (icon, label) = WidgetsCard.Parts(widget);
            var tile = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            var glyph = Theme.Label(icon, 20);
            glyph.HorizontalAlignment = HorizontalAlignment.Center;
            var text = Theme.Label(label, 11, Theme.Dim);
            text.HorizontalAlignment = HorizontalAlignment.Center;
            tile.Children.Add(glyph);
            tile.Children.Add(text);
            _preview.Children.Add(new Border
            {
                Background = Theme.Hover,
                CornerRadius = new CornerRadius(8),
                Margin = new Thickness(3),
                Height = 62,
                Padding = new Thickness(4),
                Child = tile,
            });
        }
    }

    private bool Save()
    {
        if (!_editor.TrySave(_editor.Config.WithWidgets(_widgets))) return false;
        _dirty = false;
        _status.Foreground = Theme.Green;
        _status.Text = "Guardado. El móvil recibe el panel nuevo al momento.";
        return true;
    }

    /// <summary>Abre el selector de emojis de Windows (Win+.) con el foco en el campo del icono.</summary>
    private void OpenEmojiPicker()
    {
        _icon.Focus();
        Native.Send(
            Native.Key(0x5B, up: false, extended: true), Native.Key(0xBE, up: false), Native.Key(0xBE, up: true), Native.Key(0x5B, up: true, extended: true));
    }
}
