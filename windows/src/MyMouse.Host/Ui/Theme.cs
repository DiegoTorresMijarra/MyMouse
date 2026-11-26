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

    public static readonly SolidColorBrush Bg = Brush("#1E1F22");
    public static readonly SolidColorBrush CardBg = Brush("#2B2D30");
    public static readonly SolidColorBrush Accent = Brush("#3574F0");
    public static readonly SolidColorBrush Text = Brush("#DFE1E5");
    public static readonly SolidColorBrush Dim = Brush("#868A91");
    public static readonly SolidColorBrush Line = Brush("#393B40");
    public static readonly SolidColorBrush Hover = Brush("#35373B");
    public static readonly SolidColorBrush Green = Brush("#5FB865");
    public static readonly SolidColorBrush Amber = Brush("#E5A50A");
    public static readonly SolidColorBrush Red = Brush("#F2525F");
    public static readonly SolidColorBrush Gray = Brush("#8C9097");
    public static readonly SolidColorBrush Transparent = Brush("#00000000");

    public static readonly Color AccentColor = (Color)ColorConverter.ConvertFromString("#3574F0");

    /// <summary>Segoe UI Variable si está instalada (Windows 11) y, si no, Segoe UI.</summary>
    public static readonly FontFamily Font = new("Segoe UI Variable Text, Segoe UI");

    public const double CornerRadius = 12;

    private static ControlTemplate? _buttonTemplate;
    private static ControlTemplate? _textBoxTemplate;
    private static ControlTemplate? _checkBoxTemplate;
    private static Style? _scrollBarStyle;

    /// <summary>Instala los estilos de scroll en los recursos de la aplicación. Si una plantilla falla, se usa la de Windows.</summary>
    public static void Apply(ResourceDictionary resources)
    {
        _buttonTemplate = Load("botón", """
            <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="Button">
              <Grid SnapsToDevicePixels="True">
                <Border x:Name="bg" Background="{TemplateBinding Background}" CornerRadius="8"/>
                <Border x:Name="hover" Background="White" CornerRadius="8" Opacity="0" IsHitTestVisible="False"/>
                <ContentPresenter Margin="{TemplateBinding Padding}" HorizontalAlignment="Center" VerticalAlignment="Center"/>
              </Grid>
              <ControlTemplate.Triggers>
                <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="hover" Property="Opacity" Value="0.09"/></Trigger>
                <Trigger Property="IsPressed" Value="True"><Setter TargetName="hover" Property="Opacity" Value="0.18"/></Trigger>
                <Trigger Property="IsEnabled" Value="False"><Setter Property="Opacity" Value="0.45"/></Trigger>
              </ControlTemplate.Triggers>
            </ControlTemplate>
            """);
        _textBoxTemplate = Load("cuadro de texto", """
            <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="TextBox">
              <Border Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}" BorderThickness="1" CornerRadius="6">
                <ScrollViewer x:Name="PART_ContentHost" Margin="{TemplateBinding Padding}" Focusable="False"
                              HorizontalScrollBarVisibility="Hidden" VerticalScrollBarVisibility="Hidden"/>
              </Border>
              <ControlTemplate.Triggers>
                <Trigger Property="IsKeyboardFocused" Value="True"><Setter Property="BorderBrush" Value="#3574F0"/></Trigger>
                <Trigger Property="IsEnabled" Value="False"><Setter Property="Opacity" Value="0.5"/></Trigger>
              </ControlTemplate.Triggers>
            </ControlTemplate>
            """);
        _checkBoxTemplate = Load("casilla", """
            <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="CheckBox">
              <StackPanel Orientation="Horizontal" Background="Transparent">
                <Border x:Name="box" Width="16" Height="16" CornerRadius="4" BorderThickness="1"
                        BorderBrush="#5A5D63" Background="#2B2D30" VerticalAlignment="Center">
                  <Path x:Name="tick" Data="M3.5,8.5 L6.5,11.5 L12.5,4.5" Stroke="White" StrokeThickness="2" Visibility="Collapsed"/>
                </Border>
                <ContentPresenter Margin="8,0,0,0" VerticalAlignment="Center"/>
              </StackPanel>
              <ControlTemplate.Triggers>
                <Trigger Property="IsChecked" Value="True">
                  <Setter TargetName="box" Property="Background" Value="#3574F0"/>
                  <Setter TargetName="box" Property="BorderBrush" Value="#3574F0"/>
                  <Setter TargetName="tick" Property="Visibility" Value="Visible"/>
                </Trigger>
                <Trigger Property="IsEnabled" Value="False"><Setter Property="Opacity" Value="0.5"/></Trigger>
              </ControlTemplate.Triggers>
            </ControlTemplate>
            """);

        var scrollTemplate = Load("barra de desplazamiento", """
            <ControlTemplate xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" TargetType="ScrollBar">
              <Grid Width="8" Background="Transparent">
                <Track x:Name="PART_Track" IsDirectionReversed="True">
                  <Track.DecreaseRepeatButton><RepeatButton Command="ScrollBar.PageUpCommand" Opacity="0" Focusable="False"/></Track.DecreaseRepeatButton>
                  <Track.Thumb>
                    <Thumb>
                      <Thumb.Template>
                        <ControlTemplate TargetType="Thumb"><Border Background="#4A4D53" CornerRadius="4" Margin="1,0"/></ControlTemplate>
                      </Thumb.Template>
                    </Thumb>
                  </Track.Thumb>
                  <Track.IncreaseRepeatButton><RepeatButton Command="ScrollBar.PageDownCommand" Opacity="0" Focusable="False"/></Track.IncreaseRepeatButton>
                </Track>
              </Grid>
            </ControlTemplate>
            """);
        if (scrollTemplate is not null)
        {
            _scrollBarStyle = new Style(typeof(System.Windows.Controls.Primitives.ScrollBar));
            _scrollBarStyle.Setters.Add(new Setter(Control.TemplateProperty, scrollTemplate));
            resources[typeof(System.Windows.Controls.Primitives.ScrollBar)] = _scrollBarStyle;
        }
    }

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

    public static Button AccentButton(object content) => FlatButton(content, Accent, Brushes.White);

    public static TextBox Input(string text = "", bool multiline = false)
    {
        var t = new TextBox
        {
            Text = text,
            Background = Bg,
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
