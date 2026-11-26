using System.Windows;
using System.Windows.Controls;
using System.Windows.Markup;
using System.Windows.Media;

namespace MyMouse.Host.Ui;

/// <summary>Tema oscuro de la v4 (docs/spec-v4-toolbox.md §2.2): colores, tipografía y las plantillas de los controles.</summary>
internal static class Theme
{
    public static SolidColorBrush Brush(string hex)
    {
        var brush = new SolidColorBrush((Color)ColorConverter.ConvertFromString(hex));
        brush.Freeze();
        return brush;
    }

    // ---- Tokens "glass" (docs/spec-v4.7-universo.md §3.1; sustituyen a los de la v4.5): un único sitio en Windows ----

    /// <summary>Fondo sólido de los diálogos y base del universo.</summary>
    public static readonly SolidColorBrush Bg = Brush("#12102B");

    /// <summary>Tarjeta sólida: "Efecto cristal" desactivado (#1A1830), y fondo de menús y diálogos.</summary>
    public static readonly SolidColorBrush CardSolid = Brush("#1A1830");

    /// <summary>Menús flotantes: #1A1830 casi opaco.</summary>
    public static readonly SolidColorBrush PanelSolid = Brush("#F21A1830");

    /// <summary>Cristal: tarjeta (plano): blanco al 7 %.</summary>
    public static readonly SolidColorBrush CardGlassFlat = Brush("#12FFFFFF");

    /// <summary>Cristal: tarjeta con el brillo superior (blanco del 10 % al 0 % en el 35 % de arriba, sobre el 7 %).</summary>
    public static readonly Brush CardGlassShine = Gradient(
        [(Color.FromArgb(0x2B, 255, 255, 255), 0), (Color.FromArgb(0x12, 255, 255, 255), 0.35), (Color.FromArgb(0x12, 255, 255, 255), 1)],
        new Point(0, 0), new Point(0, 1));

    /// <summary>"Efecto cristal" activado (por defecto). Lo fija <c>HostApp</c> al cambiar el ajuste; las tarjetas nuevas lo leen.</summary>
    public static bool GlassEffect { get; set; } = true;

    /// <summary>Fondo de una tarjeta: cristal con brillo o, sin el efecto, sólido.</summary>
    public static Brush CardGlass => GlassEffect ? CardGlassShine : CardSolid;

    /// <summary>Fondo plano de una caja pequeña (filas, miniaturas, campos): cristal o sólido según el ajuste.</summary>
    public static Brush CardBg => GlassEffect ? CardGlassFlat : CardSolid;

    /// <summary>Borde de la tarjeta: 1 px blanco al 16 %.</summary>
    public static readonly SolidColorBrush Line = Brush("#29FFFFFF");

    /// <summary>Elemento elevado o hover: blanco al 12 %.</summary>
    public static readonly SolidColorBrush Hover = Brush("#1FFFFFFF");

    /// <summary>Seleccionado: acento al 22 % con borde de acento al 45 %.</summary>
    public static readonly SolidColorBrush Selected = Brush("#387C8CFF");
    public static readonly SolidColorBrush SelectedBorder = Brush("#737C8CFF");

    /// <summary>Fondo de los cuadros de texto.</summary>
    public static readonly SolidColorBrush InputBg = Brush("#40000000");

    /// <summary>Acento: azul lavanda. Los botones primarios llevan <see cref="AccentGradient"/>.</summary>
    public static readonly SolidColorBrush Accent = Brush("#7C8CFF");

    /// <summary>Botones primarios: degradado #6D5BFF a #3B82F6.</summary>
    public static readonly Brush AccentGradient = Gradient(
        [((Color)ColorConverter.ConvertFromString("#6D5BFF"), 0), ((Color)ColorConverter.ConvertFromString("#3B82F6"), 1)],
        new Point(0, 0), new Point(1, 1));

    public static readonly SolidColorBrush Text = Brush("#F4F5FA");

    /// <summary>Texto secundario: #B9BDD0 (contraste mínimo de 4,5:1 sobre la tarjeta).</summary>
    public static readonly SolidColorBrush Dim = Brush("#B9BDD0");

    /// <summary>Texto terciario: #8C90A8.</summary>
    public static readonly SolidColorBrush Faint = Brush("#8C90A8");
    public static readonly SolidColorBrush Green = Brush("#5FB865");
    public static readonly SolidColorBrush Amber = Brush("#E5A50A");
    public static readonly SolidColorBrush Red = Brush("#E5534B");
    public static readonly SolidColorBrush Gray = Brush("#8C9097");
    public static readonly SolidColorBrush Transparent = Brush("#00000000");

    public static readonly Color AccentColor = (Color)ColorConverter.ConvertFromString("#7C8CFF");

    /// <summary>Segoe UI Variable si está instalada (Windows 11) y, si no, Segoe UI.</summary>
    public static readonly FontFamily Font = new("Segoe UI Variable Text, Segoe UI");

    /// <summary>Títulos: Segoe UI Variable Display.</summary>
    public static readonly FontFamily DisplayFont = new("Segoe UI Variable Display, Segoe UI");

    public static readonly FontFamily Mono = new("Cascadia Mono, Consolas");

    /// <summary>Iconos de línea del sistema: Segoe Fluent Icons (Windows 11) o Segoe MDL2 Assets (Windows 10).</summary>
    public static readonly FontFamily Icons = new("Segoe Fluent Icons, Segoe MDL2 Assets");

    // Radios: panel 16, tarjeta 12, fila o botón 8, chip 6. Espaciado en rejilla de 4.
    public const double PanelRadius = 16;
    public const double CornerRadius = 12;
    public const double RowRadius = 8;
    public const double ChipRadius = 6;

    private static LinearGradientBrush Gradient((Color Color, double Offset)[] stops, Point start, Point end)
    {
        var brush = new LinearGradientBrush { StartPoint = start, EndPoint = end };
        foreach (var (color, offset) in stops) brush.GradientStops.Add(new GradientStop(color, offset));
        brush.Freeze();
        return brush;
    }

    private static ControlTemplate? _buttonTemplate;
    private static ControlTemplate? _roundButtonTemplate;
    private static ControlTemplate? _textBoxTemplate;
    private static ControlTemplate? _checkBoxTemplate;
    private static Style? _scrollBarStyle;

    /// <summary>Instala los estilos de scroll en los recursos de la aplicación. Si una plantilla falla, se usa la de Windows.</summary>
    public static void Apply(ResourceDictionary resources)
    {
        _buttonTemplate = Load("botón", ButtonXaml(8));
        _roundButtonTemplate = Load("botón redondo", ButtonXaml(999));
        _textBoxTemplate = Load("cuadro de texto", """
            <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="TextBox">
              <Border Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}" BorderThickness="1" CornerRadius="6">
                <ScrollViewer x:Name="PART_ContentHost" Margin="{TemplateBinding Padding}" Focusable="False"
                              HorizontalScrollBarVisibility="Hidden" VerticalScrollBarVisibility="Hidden"/>
              </Border>
              <ControlTemplate.Triggers>
                <Trigger Property="IsKeyboardFocused" Value="True"><Setter Property="BorderBrush" Value="#7C8CFF"/></Trigger>
                <Trigger Property="IsEnabled" Value="False"><Setter Property="Opacity" Value="0.5"/></Trigger>
              </ControlTemplate.Triggers>
            </ControlTemplate>
            """);
        _checkBoxTemplate = Load("casilla", """
            <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="CheckBox">
              <StackPanel Orientation="Horizontal" Background="Transparent">
                <Border x:Name="box" Width="16" Height="16" CornerRadius="4" BorderThickness="1"
                        BorderBrush="#40FFFFFF" Background="#17FFFFFF" VerticalAlignment="Center">
                  <Path x:Name="tick" Data="M3.5,8.5 L6.5,11.5 L12.5,4.5" Stroke="White" StrokeThickness="2" Visibility="Collapsed"/>
                </Border>
                <ContentPresenter Margin="8,0,0,0" VerticalAlignment="Center"/>
              </StackPanel>
              <ControlTemplate.Triggers>
                <Trigger Property="IsChecked" Value="True">
                  <Setter TargetName="box" Property="Background" Value="#7C8CFF"/>
                  <Setter TargetName="box" Property="BorderBrush" Value="#7C8CFF"/>
                  <Setter TargetName="tick" Property="Visibility" Value="Visible"/>
                </Trigger>
                <Trigger Property="IsEnabled" Value="False"><Setter Property="Opacity" Value="0.5"/></Trigger>
              </ControlTemplate.Triggers>
            </ControlTemplate>
            """);

        // Barra fina (4 px) que solo se ve al pasar el ratón por el ScrollViewer.
        var scrollTemplate = Load("barra de desplazamiento", """
            <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="ScrollBar">
              <Grid x:Name="root" Width="8" Background="Transparent" Opacity="0">
                <Track x:Name="PART_Track" IsDirectionReversed="True">
                  <Track.DecreaseRepeatButton><RepeatButton Command="ScrollBar.PageUpCommand" Opacity="0" Focusable="False"/></Track.DecreaseRepeatButton>
                  <Track.Thumb>
                    <Thumb>
                      <Thumb.Template>
                        <ControlTemplate TargetType="Thumb"><Border Background="#66FFFFFF" CornerRadius="2" Margin="2,0"/></ControlTemplate>
                      </Thumb.Template>
                    </Thumb>
                  </Track.Thumb>
                  <Track.IncreaseRepeatButton><RepeatButton Command="ScrollBar.PageDownCommand" Opacity="0" Focusable="False"/></Track.IncreaseRepeatButton>
                </Track>
              </Grid>
              <ControlTemplate.Triggers>
                <DataTrigger Binding="{Binding IsMouseOver, RelativeSource={RelativeSource AncestorType=ScrollViewer}}" Value="True">
                  <Setter TargetName="root" Property="Opacity" Value="1"/>
                </DataTrigger>
              </ControlTemplate.Triggers>
            </ControlTemplate>
            """);
        if (scrollTemplate is not null)
        {
            _scrollBarStyle = new Style(typeof(System.Windows.Controls.Primitives.ScrollBar));
            _scrollBarStyle.Setters.Add(new Setter(Control.TemplateProperty, scrollTemplate));
            resources[typeof(System.Windows.Controls.Primitives.ScrollBar)] = _scrollBarStyle;
        }
    }

    private static string ButtonXaml(int radius) => $$"""
        <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="Button">
          <Grid SnapsToDevicePixels="True">
            <Border x:Name="bg" Background="{TemplateBinding Background}" CornerRadius="{{radius}}"/>
            <Border x:Name="hover" Background="White" CornerRadius="{{radius}}" Opacity="0" IsHitTestVisible="False"/>
            <ContentPresenter Margin="{TemplateBinding Padding}" HorizontalAlignment="{TemplateBinding HorizontalContentAlignment}" VerticalAlignment="{TemplateBinding VerticalContentAlignment}"/>
          </Grid>
          <ControlTemplate.Triggers>
            <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="hover" Property="Opacity" Value="0.09"/></Trigger>
            <Trigger Property="IsPressed" Value="True"><Setter TargetName="hover" Property="Opacity" Value="0.18"/></Trigger>
            <Trigger Property="IsEnabled" Value="False"><Setter Property="Opacity" Value="0.45"/></Trigger>
          </ControlTemplate.Triggers>
        </ControlTemplate>
        """;

    private static ControlTemplate? Load(string what, string xaml)
    {
        try
        {
            return (ControlTemplate)XamlReader.Parse(xaml);
        }
        catch (Exception ex)
        {
            FileLog.Write($"Plantilla de {what} no válida, se usa la de Windows: {ex.Message}");
            return null;
        }
    }

    // ---- Controles con el estilo de la v4 ----

    public static Button FlatButton(object content, Brush? background = null, Brush? foreground = null, double fontSize = 13,
        Thickness? padding = null)
    {
        var b = new Button
        {
            Content = content,
            Background = background ?? Hover,
            Foreground = foreground ?? Text,
            FontFamily = Font,
            FontSize = fontSize,
            Padding = padding ?? new Thickness(12, 6, 12, 6),
            BorderThickness = new Thickness(0),
            Cursor = System.Windows.Input.Cursors.Hand,
            Focusable = false,
        };
        if (_buttonTemplate is not null) b.Template = _buttonTemplate;
        return b;
    }

    public static Button AccentButton(object content) => FlatButton(content, AccentGradient, Brushes.White);

    public static TextBox Input(string text = "", bool multiline = false)
    {
        var t = new TextBox
        {
            Text = text,
            Background = InputBg,
            Foreground = Text,
            CaretBrush = Text,
            SelectionBrush = Accent,
            BorderBrush = Line,
            FontFamily = Font,
            FontSize = 13,
            Padding = new Thickness(8, 6, 8, 6),
            AcceptsReturn = multiline,
            TextWrapping = multiline ? TextWrapping.Wrap : TextWrapping.NoWrap,
            VerticalContentAlignment = VerticalAlignment.Center,
        };
        if (_textBoxTemplate is not null) t.Template = _textBoxTemplate;
        return t;
    }

    public static CheckBox Check(string label, bool isChecked = false)
    {
        var c = new CheckBox { Content = label, IsChecked = isChecked, Foreground = Text, FontFamily = Font, FontSize = 13, Cursor = System.Windows.Input.Cursors.Hand };
        if (_checkBoxTemplate is not null) c.Template = _checkBoxTemplate;
        return c;
    }

    /// <summary>Botón de icono (glifo de <see cref="Icons"/>) cuadrado, transparente y con hover de cristal.</summary>
    public static Button IconButton(string glyph, double size = 28, string? tooltip = null, double glyphSize = 12)
    {
        var b = FlatButton(new TextBlock
        {
            Text = glyph,
            FontFamily = Icons,
            FontSize = glyphSize,
            Foreground = Text,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        }, Transparent, padding: new Thickness(0));
        b.Width = b.Height = size;
        b.HorizontalContentAlignment = HorizontalAlignment.Center;
        if (tooltip is not null) b.ToolTip = tooltip;
        return b;
    }

    /// <summary>Botón circular de cristal con un glifo de <see cref="Icons"/> (controles de música).</summary>
    public static Button RoundButton(TextBlock glyph, double size = 36)
    {
        glyph.FontFamily = Icons;
        glyph.HorizontalAlignment = HorizontalAlignment.Center;
        glyph.VerticalAlignment = VerticalAlignment.Center;
        var b = FlatButton(glyph, Hover, padding: new Thickness(0));
        b.Width = b.Height = size;
        b.HorizontalContentAlignment = HorizontalAlignment.Center;
        if (_roundButtonTemplate is not null) b.Template = _roundButtonTemplate;
        return b;
    }

    public static TextBlock Glyph(string glyph, double size = 14, Brush? color = null) => new()
    {
        Text = glyph,
        FontFamily = Icons,
        FontSize = size,
        Foreground = color ?? Text,
        VerticalAlignment = VerticalAlignment.Center,
    };

    /// <summary>Chip: texto pequeño sobre un fondo de cristal (radio 6). Monoespaciado si <paramref name="mono"/>.</summary>
    public static Border Chip(string text, Brush? foreground = null, Brush? background = null, bool mono = false, double size = 11)
    {
        var label = Label(text, size, foreground ?? Dim);
        if (mono) label.FontFamily = Mono;
        return new Border
        {
            Background = background ?? Hover,
            CornerRadius = new CornerRadius(ChipRadius),
            Padding = new Thickness(6, 1, 6, 2),
            Child = label,
            HorizontalAlignment = HorizontalAlignment.Left,
        };
    }

    /// <summary>Alto fijo de la píldora de estado; su radio es la mitad (docs/spec-v4.7-universo.md §3.2).</summary>
    public const double PillHeight = 26;

    /// <summary>
    /// Píldora de estado nítida: alto fijo de 26 px y <c>CornerRadius</c> igual a la mitad, relleno de 10 px a los lados, fondo del
    /// color de estado al 16 % con borde al 40 %, y punto de 8 px con un halo suave. Sin desenfoque ni opacidad sobre el texto.
    /// </summary>
    public static Border StatusPill(Brush status, string text)
    {
        var color = status is SolidColorBrush solid ? solid.Color : Colors.Gray;
        Brush Tint(byte alpha) => Brush($"#{alpha:X2}{color.R:X2}{color.G:X2}{color.B:X2}");
        var label = Label(text, 12, Text, FontWeights.SemiBold);
        label.VerticalAlignment = VerticalAlignment.Center;
        label.Margin = new Thickness(8, 0, 0, 0);
        label.TextTrimming = TextTrimming.None;
        label.TextWrapping = TextWrapping.NoWrap;
        var dot = new Grid { Width = 14, Height = 14, VerticalAlignment = VerticalAlignment.Center };
        dot.Children.Add(new System.Windows.Shapes.Ellipse { Fill = Tint(0x40) }); // halo
        dot.Children.Add(new System.Windows.Shapes.Ellipse { Width = 8, Height = 8, Fill = status });
        var row = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        row.Children.Add(dot);
        row.Children.Add(label);
        return new Border
        {
            Height = PillHeight,
            CornerRadius = new CornerRadius(PillHeight / 2),
            Padding = new Thickness(10, 0, 10, 0),
            Background = Tint(0x29),
            BorderBrush = Tint(0x66),
            BorderThickness = new Thickness(1),
            HorizontalAlignment = HorizontalAlignment.Left,
            SnapsToDevicePixels = true,
            UseLayoutRounding = true,
            Child = row,
        };
    }

    /// <summary>Título de tarjeta: mayúsculas pequeñas (11 px), color secundario. (WPF no tiene espaciado de letras: se omite el +0,06 em.)</summary>
    public static TextBlock CardTitle(string text)
    {
        var t = Label(text.ToUpperInvariant(), 11, Dim, FontWeights.SemiBold);
        t.FontFamily = DisplayFont;
        return t;
    }

    public static TextBlock Label(string text, double size = 13, Brush? color = null, FontWeight? weight = null) => new()
    {
        Text = text,
        FontFamily = Font,
        FontSize = size,
        Foreground = color ?? Text,
        FontWeight = weight ?? FontWeights.Normal,
        TextTrimming = TextTrimming.CharacterEllipsis,
    };
}
