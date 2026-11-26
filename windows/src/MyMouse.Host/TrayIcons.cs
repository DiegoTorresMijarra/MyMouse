using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;
using MyMouse.Host.Ui;

namespace MyMouse.Host;

/// <summary>
/// Iconos de la bandeja (docs/spec-v4.7-universo.md §3.3): el icono de la app (16, 20, 24 o 32 px según la escala de la pantalla) con un
/// punto de estado superpuesto abajo a la derecha (verde, ámbar, rojo o gris). Se dibujan al arrancar a partir del .ico embebido.
/// Si no se pudiera leer, se usa el icono del propio exe, sin punto.
/// </summary>
internal static class TrayIcons
{
    public static readonly Icon NoDevice = Create(Color.FromArgb(0xE5, 0x53, 0x4B));  // rojo: sin móvil o sin adb
    public static readonly Icon Waiting = Create(Color.FromArgb(0xE5, 0xA5, 0x0A));   // ámbar: móvil visto, app sin conectar
    public static readonly Icon Connected = Create(Color.FromArgb(0x5F, 0xB8, 0x65)); // verde
    public static readonly Icon Paused = Create(Color.FromArgb(0x8C, 0x90, 0x97));    // gris

    private static Icon Create(Color status)
    {
        var size = TraySize();
        try
        {
            using var stream = AppIcon.OpenStream();
            if (stream is not null)
            {
                using var baseIcon = new Icon(stream, new Size(size, size));
                using var bmp = baseIcon.ToBitmap();
                using var canvas = new Bitmap(size, size, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
                using (var g = Graphics.FromImage(canvas))
                {
                    g.SmoothingMode = SmoothingMode.AntiAlias;
                    g.Clear(Color.Transparent);
                    g.DrawImage(bmp, new Rectangle(0, 0, size, size));
                    DrawDot(g, size, status);
                }
                return FromBitmap(canvas);
            }
        }
        catch (Exception ex) when (ex is ArgumentException or ExternalException or IOException)
        {
            FileLog.Write($"No se pudo preparar el icono de la bandeja: {ex.Message}");
        }
        return Fallback();
    }

    /// <summary>Tamaño del icono de bandeja para la escala actual: 16 al 100 %, 20 al 125 %, 24 al 150 % y 32 al 200 % o más.</summary>
    private static int TraySize()
    {
        var small = System.Windows.Forms.SystemInformation.SmallIconSize.Width;
        return small switch { <= 16 => 16, <= 20 => 20, <= 24 => 24, _ => 32 };
    }

    /// <summary>Punto de estado abajo a la derecha con un borde oscuro fino, para que se lea sobre cualquier fondo.</summary>
    private static void DrawDot(Graphics g, int size, Color status)
    {
        var d = Math.Max(6, (int)Math.Round(size * 0.46));
        var x = size - d;
        var y = size - d;
        using var outline = new SolidBrush(Color.FromArgb(235, 18, 16, 43));
        using var fill = new SolidBrush(status);
        g.FillEllipse(outline, x - 0.5f, y - 0.5f, d + 1, d + 1);
        var inset = Math.Max(1f, size / 16f);
        g.FillEllipse(fill, x + inset, y + inset, d - 2 * inset, d - 2 * inset);
    }

    private static Icon FromBitmap(Bitmap bmp)
    {
        var handle = bmp.GetHicon();
        try
        {
            return (Icon)Icon.FromHandle(handle).Clone();
        }
        finally
        {
            DestroyIcon(handle);
        }
    }

    private static Icon Fallback() => Icon.ExtractAssociatedIcon(Environment.ProcessPath ?? "") ?? SystemIcons.Application;

    [DllImport("user32.dll")]
    private static extern bool DestroyIcon(IntPtr handle);
}
