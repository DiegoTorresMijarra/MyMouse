using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;

namespace MyMouse.Host;

/// <summary>Iconos de bandeja dibujados en tiempo de ejecución: un ratón con un punto de color según el estado.</summary>
internal static class TrayIcons
{
    public static readonly Icon NoDevice = Create(Color.FromArgb(220, 60, 60));    // rojo: sin móvil o sin adb
    public static readonly Icon Waiting = Create(Color.FromArgb(230, 170, 30));    // ámbar: móvil visto, app sin conectar
    public static readonly Icon Connected = Create(Color.FromArgb(50, 180, 80));   // verde
    public static readonly Icon Paused = Create(Color.FromArgb(140, 140, 140));    // gris

    private static Icon Create(Color status)
    {
        const int size = 32;
        using var bmp = new Bitmap(size, size);
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.Clear(Color.Transparent);

            // Cuerpo del ratón.
            using var body = new GraphicsPath();
            body.AddArc(4, 2, 18, 18, 180, 180);
            body.AddLine(22, 11, 22, 21);
            body.AddArc(4, 12, 18, 18, 0, 180);
            body.CloseFigure();
            using var fill = new SolidBrush(Color.FromArgb(235, 235, 235));
            using var outline = new Pen(Color.FromArgb(60, 60, 60), 2);
            g.FillPath(fill, body);
            g.DrawPath(outline, body);
            g.DrawLine(outline, 13, 3, 13, 11);

            // Punto de estado.
            using var dot = new SolidBrush(status);
            using var dotOutline = new Pen(Color.White, 2);
            g.FillEllipse(dot, 17, 17, 14, 14);
            g.DrawEllipse(dotOutline, 17, 17, 14, 14);
        }

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

    [DllImport("user32.dll")]
    private static extern bool DestroyIcon(IntPtr handle);
}
