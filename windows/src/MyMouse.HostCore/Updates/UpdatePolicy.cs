namespace MyMouse.HostCore.Updates;

/// <summary>Quién habla en un aviso cruzado.</summary>
public enum VersionSide
{
    Pc,
    Phone,
}

public enum CrossDeviceKind
{
    /// <summary>Sin aviso: misma versión, desconocida o alguna de desarrollo.</summary>
    None,

    /// <summary>El otro dispositivo tiene una versión más alta: se puede actualizar este.</summary>
    OtherNewer,

    /// <summary>El otro dispositivo tiene una versión más baja: solo se informa, se actualiza desde el otro.</summary>
    OtherOlder,
}

/// <summary>Resultado de comparar la versión de este dispositivo con la del otro (docs/spec-v5.1-actualizaciones.md §6).</summary>
public sealed record CrossDeviceNotice(CrossDeviceKind Kind, string Message, AppVersion? OtherVersion)
{
    public static CrossDeviceNotice None { get; } = new(CrossDeviceKind.None, "", null);
}

/// <summary>Decisiones puras de las actualizaciones: si avisar y qué decir.</summary>
public static class UpdatePolicy
{
    /// <summary>
    /// ¿Se ofrece <paramref name="latest"/> a quien tiene <paramref name="local"/>? No si la versión propia es "dev" o no se entiende, si la release no es
    /// más alta, o si el usuario la omitió (<paramref name="skipped"/>). Una release posterior a la omitida sí se ofrece.
    /// </summary>
    public static bool ShouldOffer(AppVersion? local, ReleaseInfo? latest, string? skipped)
    {
        if (latest is null || local is not { IsDev: false } mine) return false;
        if (latest.Version <= mine) return false;
        return !IsSkipped(latest.Version, skipped);
    }

    /// <summary>¿Es <paramref name="version"/> la versión que el usuario decidió omitir? (Se guarda como texto: <c>5.2</c>.)</summary>
    public static bool IsSkipped(AppVersion version, string? skipped) =>
        AppVersion.Parse(skipped) is { } s && s.SameNumbers(version);

    /// <summary>
    /// Aviso cruzado. <paramref name="mine"/> es la versión de este dispositivo y <paramref name="other"/> la que dijo el otro por USB. No hay aviso
    /// si alguna se desconoce o es "dev", ni si son iguales.
    /// </summary>
    public static CrossDeviceNotice CrossDevice(VersionSide side, string? mine, string? other)
    {
        if (AppVersion.Parse(mine) is not { IsDev: false } a || AppVersion.Parse(other) is not { IsDev: false } b) return CrossDeviceNotice.None;
        var cmp = b.CompareTo(a);
        if (cmp == 0) return CrossDeviceNotice.None;

        var otherName = side == VersionSide.Pc ? CoreLoc.T("up_the_phone") : CoreLoc.T("up_the_pc");
        var mineName = side == VersionSide.Pc ? CoreLoc.T("up_this_pc") : CoreLoc.T("up_this_phone");
        if (cmp > 0)
            return new(CrossDeviceKind.OtherNewer, CoreLoc.F("up_other_newer", otherName, b.Display, mineName, a.Display), b);

        var where = side == VersionSide.Pc ? CoreLoc.T("up_from_phone") : CoreLoc.T("up_from_pc");
        return new(CrossDeviceKind.OtherOlder, CoreLoc.F("up_other_older", otherName, b.Display, where), b);
    }

    /// <summary>Texto corto del banner de una versión nueva.</summary>
    public static string NewVersionText(ReleaseInfo release) => CoreLoc.F("up_new_version", release.Version.Display);

    /// <summary>Notas de la release recortadas para una hoja o un aviso: sin líneas en blanco de más y como mucho <paramref name="max"/> caracteres.</summary>
    public static string TrimNotes(string notes, int max = 600)
    {
        var text = string.Join("\n", notes.Replace("\r", "").Split('\n').Select(l => l.TrimEnd())).Trim();
        while (text.Contains("\n\n\n", StringComparison.Ordinal)) text = text.Replace("\n\n\n", "\n\n", StringComparison.Ordinal);
        if (text.Length <= max) return text;
        var cut = text[..max];
        var lastBreak = cut.LastIndexOfAny(['\n', ' ']);
        if (lastBreak > max / 2) cut = cut[..lastBreak];
        return cut.TrimEnd() + "…";
    }
}
