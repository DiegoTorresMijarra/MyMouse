using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using MyMouse.HostCore.Toolbox;

namespace MyMouse.Host.Ui;

/// <summary>"Personalizar tarjetas…" (docs/spec-v4-toolbox.md §2.3): marcar o desmarcar cada tarjeta (salvo Opciones) y reordenarlas con ↑ ↓.</summary>
internal sealed class CustomizeCardsWindow : Window
{
    private readonly HostApp _app;
    private readonly StackPanel _rows = new();

    public CustomizeCardsWindow(HostApp app)
    {
        _app = app;
        Title = "Personalizar tarjetas";
        Width = 380;
        SizeToContent = SizeToContent.Height;
        ResizeMode = ResizeMode.NoResize;
        ShowInTaskbar = false;
        Topmost = true;
        Background = Theme.Bg;
        FontFamily = Theme.Font;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape) Close();
        };

        var panel = new StackPanel { Margin = new Thickness(20) };
        panel.Children.Add(Theme.Label("Personalizar tarjetas", 17, weight: FontWeights.SemiBold));
        var hint = Theme.Label("Marca las que quieres ver y ordénalas con las flechas. Opciones siempre está.", 12, Theme.Dim);
        hint.TextWrapping = TextWrapping.Wrap;
        hint.TextTrimming = TextTrimming.None;
        hint.Margin = new Thickness(0, 6, 0, 14);
        panel.Children.Add(hint);
        panel.Children.Add(_rows);
        var glass = Theme.Check("Efecto cristal (tarjetas translúcidas; desmarcado, tarjetas sólidas)", _app.Layout.GlassEffect);
        glass.Margin = new Thickness(0, 6, 0, 0);
        glass.Click += (_, _) => _app.Layout = _app.Layout.WithGlass(glass.IsChecked == true);
        panel.Children.Add(glass);
        var close = Theme.AccentButton("Listo");
        close.HorizontalAlignment = HorizontalAlignment.Right;
        close.Margin = new Thickness(0, 16, 0, 0);
        close.Click += (_, _) => Close();
        panel.Children.Add(close);
        Content = new Border { Background = Theme.Bg, Child = panel };
        Rebuild();
    }

    private static string NameOf(ToolboxCard card) => card switch
    {
        ToolboxCard.Options => "Opciones",
        ToolboxCard.Music => "Música",
        ToolboxCard.Gestures => "Gestos",
        _ => "Widgets del móvil",
    };

    private void Rebuild()
    {
        _rows.Children.Clear();
        foreach (var (card, visible) in _app.Layout.Entries)
        {
            var c = card;
            var row = new DockPanel { Margin = new Thickness(0, 0, 0, 8) };
            var down = Theme.FlatButton("↓", padding: new Thickness(8, 3, 8, 3));
            var up = Theme.FlatButton("↑", padding: new Thickness(8, 3, 8, 3));
            up.Margin = new Thickness(0, 0, 6, 0);
            down.Click += (_, _) => Apply(_app.Layout.Move(c, 1));
            up.Click += (_, _) => Apply(_app.Layout.Move(c, -1));
            var fixedCard = card == ToolboxCard.Options;
            up.IsEnabled = down.IsEnabled = !fixedCard;
            DockPanel.SetDock(down, Dock.Right);
            DockPanel.SetDock(up, Dock.Right);
            row.Children.Add(down);
            row.Children.Add(up);
            var check = Theme.Check(NameOf(card), visible);
            check.IsEnabled = !fixedCard;
            check.Click += (_, _) => Apply(_app.Layout.SetVisible(c, check.IsChecked == true));
            row.Children.Add(check);
            _rows.Children.Add(row);
        }
    }

    private void Apply(ToolboxLayout layout)
    {
        _app.Layout = layout;
        Rebuild();
    }
}
