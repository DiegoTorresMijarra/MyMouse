namespace MyMouse.Host;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        using var mutex = new Mutex(initiallyOwned: true, @"Local\MyMouse.Host", out var isFirstInstance);
        if (!isFirstInstance)
            return;

        ApplicationConfiguration.Initialize();
        FileLog.Cleanup();
        FileLog.Write("Arrancando MyMouse");

        Application.Run(new TrayContext(HostConfig.Load()));
    }
}
