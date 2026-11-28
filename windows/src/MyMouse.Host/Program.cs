using System.Windows;
using MyMouse.Host.Ui;

namespace MyMouse.Host;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        using var mutex = new Mutex(initiallyOwned: true, @"Local\MyMouse.Host", out var isFirstInstance);
        if (!isFirstInstance)
            return;

        FileLog.Cleanup();
        FileLog.Write("Arrancando MyMouse");

        // WPF sin App.xaml: la aplicación se crea aquí y vive en la bandeja hasta que se pulsa Salir.
        var app = new Application { ShutdownMode = ShutdownMode.OnExplicitShutdown };
        Theme.Apply(app.Resources);
        app.DispatcherUnhandledException += (_, e) =>
        {
            // Un error en una ventana no debe tumbar el servidor que atiende al móvil.
            FileLog.Write($"Error no controlado en la interfaz: {e.Exception}");
            e.Handled = true;
        };

        using var host = new HostApp(HostConfig.Load());
        app.Run();
    }
}
