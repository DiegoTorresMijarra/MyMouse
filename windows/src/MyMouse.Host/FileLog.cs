namespace MyMouse.Host;

/// <summary>Log diario en %APPDATA%\MyMouse\logs. Conserva los últimos 7 ficheros.</summary>
internal static class FileLog
{
    private const int KeepFiles = 7;
    private static readonly Lock Gate = new();

    public static string Directory => Path.Combine(HostConfig.Directory, "logs");

    public static void Write(string message)
    {
        try
        {
            lock (Gate)
            {
                System.IO.Directory.CreateDirectory(Directory);
                var file = Path.Combine(Directory, $"mymouse-{DateTime.Now:yyyy-MM-dd}.log");
                File.AppendAllText(file, $"{DateTime.Now:HH:mm:ss.fff} {message}{Environment.NewLine}");
            }
        }
        catch (IOException)
        {
            // El log nunca debe tumbar la aplicación.
        }
    }

    public static void Cleanup()
    {
        try
        {
            foreach (var old in new DirectoryInfo(Directory).GetFiles("mymouse-*.log")
                         .OrderByDescending(f => f.Name).Skip(KeepFiles))
                old.Delete();
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }
}
