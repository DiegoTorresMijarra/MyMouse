namespace MyMouse.HostCore.Gestures;

/// <summary>
/// Lee y guarda <c>gestures.json</c> desde el editor (docs/spec-v4-toolbox.md §4.1): reescribe con formato limpio, hace una
/// copia <c>.bak</c> antes de la primera reescritura (solo una vez, para conservar el fichero original con sus comentarios)
/// y no pisa un fichero que tenga errores de sintaxis.
/// </summary>
public sealed class GestureConfigStorage(string path)
{
    public string Path { get; } = path;

    public string BackupPath => Path + ".bak";

    /// <summary>
    /// Lee la configuración actual. Un fichero que no existe da una configuración vacía; uno con errores de sintaxis lanza
    /// <see cref="FormatException"/>: el editor lo dice y ofrece abrirlo en el Bloc de notas en vez de pisarlo.
    /// </summary>
    public GestureConfig Load() => File.Exists(Path) ? GestureConfig.Parse(File.ReadAllText(Path)) : GestureConfig.Empty;

    /// <summary>
    /// Guarda la configuración. Lanza <see cref="FormatException"/> si el fichero actual no se puede leer como JSON (no se toca).
    /// </summary>
    public void Save(GestureConfig config)
    {
        if (File.Exists(Path))
        {
            GestureConfig.Parse(File.ReadAllText(Path)); // si tiene errores, lanza antes de tocar nada
            if (!File.Exists(BackupPath)) File.Copy(Path, BackupPath);
        }
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(System.IO.Path.GetFullPath(Path))!);
        var temp = Path + ".tmp";
        File.WriteAllText(temp, config.ToJson(), new System.Text.UTF8Encoding(false));
        File.Move(temp, Path, overwrite: true);
    }
}
