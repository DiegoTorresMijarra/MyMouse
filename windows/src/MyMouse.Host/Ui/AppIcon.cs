using System.Reflection;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace MyMouse.Host.Ui;

/// <summary>
/// El icono de la app (docs/spec-v4.7-universo.md §3.3), embebido en el exe como recurso <c>MyMouse.ico</c> (multi-tamaño). Lo generan
/// <c>tools/icon/make_icons.py</c>; también es el <c>ApplicationIcon</c> del exe. Aquí lo usan las ventanas (<c>Window.Icon</c>) y la bandeja.
/// </summary>
internal static class AppIcon
{
    public const string ResourceName = "MyMouse.ico";

    private static readonly Lazy<ImageSource?> FrameLazy = new(LoadFrame);

    /// <summary>Para <c>Window.Icon</c>: WPF elige el tamaño que necesita de entre los del .ico. Null si no se pudo leer (no es grave).</summary>
    public static ImageSource? Frame => FrameLazy.Value;

    /// <summary>Abre el .ico embebido, o null si falta.</summary>
    public static Stream? OpenStream() => Assembly.GetExecutingAssembly().GetManifestResourceStream(ResourceName);

    private static ImageSource? LoadFrame()
    {
        try
        {
            using var stream = OpenStream();
            if (stream is null) return null;
            var decoder = new IconBitmapDecoder(stream, BitmapCreateOptions.None, BitmapCacheOption.OnLoad);
            var frame = decoder.Frames.OrderByDescending(f => f.PixelWidth).First();
            frame.Freeze();
            return frame;
        }
        catch (Exception ex) when (ex is IOException or NotSupportedException or InvalidOperationException or ArgumentException)
        {
            FileLog.Write($"No se pudo leer el icono de la app: {ex.Message}");
            return null;
        }
    }
}
