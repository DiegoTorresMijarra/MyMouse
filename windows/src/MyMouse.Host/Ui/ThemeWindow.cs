using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;
using MyMouse.HostCore.Theming;

namespace MyMouse.Host.Ui;

/// <summary>
/// "Tema…" en los Ajustes de la toolbox (docs/spec-v4.8-temas.md §3): rejilla de 4 columnas con muestras de 72 × 96 que dibujan el
/// fondo real en pequeño, el acento (Automático y 7 colores) y las casillas de estrellas. Cada cambio se aplica al momento a la
/// toolbox, al gesto en vivo y al editor, y se guarda en <c>toolbox.json</c>. Por cable se sincroniza con el móvil (gana el último cambio).
/// </summary>
internal sealed class ThemeWindow : Window
{
    private const double SwatchW = 72;
    private const double SwatchH = 96;

    private readonly HostApp _app;
    private readonly WrapPanel _swatches = new() { HorizontalAlignment = HorizontalAlignment.Left };
    private readonly StackPanel _accents = new() { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
    private readonly StackPanel _stars = new();

    public ThemeWindow(HostApp app)
    {
        _app = app;
        Title = "Tema";
        Width = 4 * (SwatchW + 16) + 2 * 20 + 24;
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
        panel.Children.Add(Theme.Label("Tema", 17, weight: FontWeights.SemiBold));
        var hint = Theme.Label("Por cable se sincroniza con el móvil: gana el último cambio.", 12, Theme.Dim);
        hint.TextWrapping = TextWrapping.Wrap;
        hint.TextTrimming = TextTrimming.None;
        hint.Margin = new Thickness(0, 6, 0, 14);
        panel.Children.Add(hint);
        panel.Children.Add(_swatches);
        var accentTitle = Theme.Label("Acento", 13, Theme.Dim, FontWeights.SemiBold);
        accentTitle.Margin = new Thickness(0, 14, 0, 0);
        panel.Children.Add(accentTitle);
        panel.Children.Add(_accents);
        _stars.Margin = new Thickness(0, 14, 0, 0);
        panel.Children.Add(_stars);
        var close = Theme.AccentButton("Listo");
        close.HorizontalAlignment = HorizontalAlignment.Right;
        close.Margin = new Thickness(0, 16, 0, 0);
        close.Click += (_, _) => Close();
        panel.Children.Add(close);
        Content = new Border { Background = Theme.Bg, Child = panel };
        Rebuild();
    }

    private void Choose(ThemeChoice choice)
    {
        if (choice.Normalized() == _app.Layout.Theme) return;
        _app.Layout = _app.Layout.WithTheme(choice, DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()); // aplica el tema, guarda toolbox.json y reconstruye la toolbox
        Rebuild();
    }

    private void Rebuild()
    {
        var choice = _app.Layout.Theme;
        _swatches.Children.Clear();
        foreach (var theme in ThemeCatalog.All)
            _swatches.Children.Add(Swatch(theme, selected: theme.Id == choice.ThemeId, c => Choose(choice with { ThemeId = theme.Id })));

        _accents.Children.Clear();
        _accents.Children.Add(Dot(choice.Theme.Accent, "Auto", "Automático (el del tema)", choice.AccentId == ThemeCatalog.AccentAuto,
            () => Choose(choice with { AccentId = ThemeCatalog.AccentAuto })));
        foreach (var a in ThemeCatalog.Accents)
            _accents.Children.Add(Dot(a.Color, null, a.Name, choice.AccentId == a.Id, () => Choose(choice with { AccentId = a.Id })));

        _stars.Children.Clear();
        if (choice.Theme.Stars)
        {
            var stars = Theme.Check("Estrellas", choice.Stars);
            stars.Click += (_, _) => Choose(choice with { Stars = stars.IsChecked == true });
            _stars.Children.Add(stars);
            if (choice.Stars)
            {
                var twinkle = Theme.Check("Animación de estrellas (parpadeo)", choice.Twinkle);
                twinkle.Margin = new Thickness(0, 8, 0, 0);
                twinkle.Click += (_, _) => Choose(choice with { Twinkle = twinkle.IsChecked == true });
                _stars.Children.Add(twinkle);
            }
        }
        else
        {
            _stars.Children.Add(Theme.Label("Este tema no lleva estrellas.", 12, Theme.Dim));
        }
    }

    /// <summary>Muestra: el fondo real del tema en 72 × 96, el nombre debajo y, si es la elegida, un borde de acento de 2 px.</summary>
    private static FrameworkElement Swatch(ThemeDef theme, bool selected, Action<ThemeDef> click)
    {
        var art = new UniverseBackground
        {
            Width = SwatchW,
            Height = SwatchH,
            CornerRadius = Theme.CornerRadius,
            Animated = false,
            Fixed = new ThemeChoice(theme.Id),
        };
        var card = new Border
        {
            Width = 40,
            Height = 24,
            Margin = new Thickness(0, 14, 0, 0),
            VerticalAlignment = VerticalAlignment.Top,
            HorizontalAlignment = HorizontalAlignment.Center,
            CornerRadius = new CornerRadius(5),
            Background = Theme.CardGlassFlat,
            BorderBrush = Theme.Line,
            BorderThickness = new Thickness(1),
        };
        var dot = new Ellipse
        {
            Width = 10,
            Height = 10,
            Margin = new Thickness(0, 52, 0, 0),
            VerticalAlignment = VerticalAlignment.Top,
            HorizontalAlignment = HorizontalAlignment.Center,
            Fill = Theme.Brush(ThemeCatalog.Hex(theme.Accent)),
        };
        var frame = new Border
        {
            CornerRadius = new CornerRadius(Theme.CornerRadius),
            BorderThickness = new Thickness(selected ? 2 : 1),
            BorderBrush = selected ? Theme.Accent : Theme.Line,
            IsHitTestVisible = false,
        };
        var box = new Grid { Width = SwatchW, Height = SwatchH };
        box.Children.Add(art);
        box.Children.Add(card);
        box.Children.Add(dot);
        box.Children.Add(frame);

        var name = Theme.Label(theme.Name, 12, selected ? Theme.Text : Theme.Dim);
        name.HorizontalAlignment = HorizontalAlignment.Center;
        name.TextAlignment = TextAlignment.Center;
        name.Margin = new Thickness(0, 6, 0, 0);
        name.MaxWidth = SwatchW + 12;
        var cell = new StackPanel { Margin = new Thickness(0, 0, 16, 12), Cursor = Cursors.Hand, Background = Theme.Transparent, ToolTip = theme.Name };
        cell.Children.Add(box);
        cell.Children.Add(name);
        cell.MouseLeftButtonUp += (_, _) => click(theme);
        return cell;
    }

    /// <summary>Círculo de acento de 32 px; el elegido lleva un anillo claro. Con <paramref name="text"/> ("Auto") lo lleva escrito.</summary>
    private static FrameworkElement Dot(int rgb, string? text, string tooltip, bool selected, Action click)
    {
        var color = Color.FromRgb((byte)(rgb >> 16), (byte)(rgb >> 8), (byte)rgb);
        var grid = new Grid { Width = 34, Height = 34, Margin = new Thickness(0, 0, 8, 0), Cursor = Cursors.Hand, Background = Theme.Transparent, ToolTip = tooltip };
        grid.Children.Add(new Ellipse { Width = 26, Height = 26, Fill = new SolidColorBrush(color), Stroke = Theme.Line, StrokeThickness = 1 });
        if (selected) grid.Children.Add(new Ellipse { Width = 32, Height = 32, Stroke = Theme.Text, StrokeThickness = 2 });
        if (text is not null)
        {
            var luma = 0.2126 * color.R / 255 + 0.7152 * color.G / 255 + 0.0722 * color.B / 255;
            grid.Children.Add(new TextBlock
            {
                Text = text,
                FontSize = 10,
                FontWeight = FontWeights.SemiBold,
                Foreground = luma > 0.55 ? Brushes.Black : Brushes.White,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center,
            });
        }
        grid.MouseLeftButtonUp += (_, _) => click();
        return grid;
    }
}
