using MyMouse.HostCore.Phone;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Theming;

/// <summary>
/// Lo que se sincroniza del tema entre móvil y PC (docs/spec-v6.1-tema-sincronizado.md): tema, acento, estrellas y
/// cuándo se eligieron (ms desde 1970, UTC; 0 = nunca). La animación de las estrellas es de cada lado y no viaja.
/// </summary>
public sealed record SyncedTheme(string ThemeId, string AccentId, bool Stars, long ChangedAtMs)
{
    public bool SameLook(SyncedTheme other) => ThemeId == other.ThemeId && AccentId == other.AccentId && Stars == other.Stars;
}

/// <summary>La regla de la sincronización. Lógica pura, la misma que <c>dev.mymouse.core.theme.ThemeSync</c> en el móvil.</summary>
public static class ThemeSync
{
    /// <summary>
    /// Llega el tema del otro lado: devuelve lo que este lado debe guardar, o null si se queda con el suyo.
    /// <list type="bullet">
    /// <item>Gana el cambio más reciente: se adopta el remoto entero, con su instante.</item>
    /// <item>Mismo instante y distinto aspecto (por ejemplo, los dos sin tocar nunca): gana el móvil.</item>
    /// <item>Mismo aspecto: solo se adopta el instante si es más reciente, para no volver a mandarlo.</item>
    /// </list>
    /// </summary>
    public static SyncedTheme? Resolve(SyncedTheme local, SyncedTheme remote, bool localIsPhone)
    {
        if (remote.ChangedAtMs > local.ChangedAtMs) return remote;
        if (remote.ChangedAtMs < local.ChangedAtMs || local.SameLook(remote)) return null;
        return localIsPhone ? null : remote;
    }
}

/// <summary>
/// Sincroniza el tema del PC con el móvil. Al conectar, <see cref="ClientSession"/> envía el tema local (vía
/// <see cref="PhoneHub.LocalTheme"/>); el móvil hace lo mismo. Cada lado aplica <see cref="ThemeSync.Resolve"/> al recibir.
/// <see cref="Adopted"/> se dispara en el hilo de la sesión.
/// </summary>
public sealed class ThemeSyncService : IDisposable
{
    private readonly PhoneHub _hub;
    private readonly Func<SyncedTheme> _local;

    public ThemeSyncService(PhoneHub hub, Func<SyncedTheme> local)
    {
        _hub = hub;
        _local = local;
        _hub.LocalTheme = () => ToMessage(_local());
        _hub.Received += OnReceived;
    }

    /// <summary>El tema del móvil gana: hay que aplicarlo y guardarlo (con su instante, para no reenviarlo).</summary>
    public event Action<SyncedTheme>? Adopted;

    /// <summary>Se cambió el tema en este PC: se manda al móvil si está conectado. No espera.</summary>
    public void LocalChanged()
    {
        if (!_hub.Connected) return;
        _ = _hub.SendAsync(ToMessage(_local()));
    }

    private void OnReceived(Message message)
    {
        if (message is not ThemeMessage { FromHost: false } m) return;
        var remote = new SyncedTheme(m.ThemeId, m.AccentId, m.Stars, m.ChangedAtMs);
        if (ThemeSync.Resolve(_local(), remote, localIsPhone: false) is { } adopted) Adopted?.Invoke(adopted);
    }

    public static ThemeMessage ToMessage(SyncedTheme t) => new(t.ThemeId, t.AccentId, t.Stars, t.ChangedAtMs, FromHost: true);

    public void Dispose()
    {
        _hub.Received -= OnReceived;
        _hub.LocalTheme = null;
    }
}
