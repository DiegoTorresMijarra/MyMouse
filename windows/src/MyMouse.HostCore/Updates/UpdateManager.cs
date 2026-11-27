namespace MyMouse.HostCore.Updates;

public enum BannerKind
{
    /// <summary>Hay una release más nueva en GitHub.</summary>
    NewVersion,

    /// <summary>El móvil tiene una versión más alta que este PC.</summary>
    PhoneNewer,

    /// <summary>El móvil tiene una versión más baja que este PC (solo informa).</summary>
    PhoneOlder,
}

/// <summary>Un aviso de la toolbox. <see cref="Release"/> es lo que instalaría "Actualizar" (null si no se encontró en GitHub).</summary>
public sealed record UpdateBanner(BannerKind Kind, string Text, ReleaseInfo? Release, bool CanInstall, bool CanSkip);

public enum CheckOutcome
{
    UpToDate,
    Available,

    /// <summary>Hay una release más nueva, pero el usuario la omitió.</summary>
    Skipped,

    /// <summary>Esta es una compilación de desarrollo: no avisa.</summary>
    DevBuild,
    Failed,
}

/// <summary>
/// Estado de las actualizaciones del PC, sin interfaz: busca releases, decide si hay que avisar y qué avisos mostrar (el de versión nueva y el
/// cruzado con el móvil). La programación (retraso inicial, cada 12 h) la hace quien lo usa. Todo es del hilo que lo llame; <see cref="Changed"/>
/// se dispara en él.
/// </summary>
public sealed class UpdateManager
{
    private readonly Func<CancellationToken, Task<IReadOnlyList<ReleaseInfo>>> _fetch;
    private readonly Action<UpdateSettings> _save;
    private readonly Action<string>? _log;
    private IReadOnlyList<ReleaseInfo> _releases = [];
    private string? _phoneVersion;

    public UpdateManager(string? localVersion, Func<CancellationToken, Task<IReadOnlyList<ReleaseInfo>>> fetch, UpdateSettings settings,
        Action<UpdateSettings> save, Action<string>? log = null)
    {
        LocalText = AppVersion.Parse(localVersion)?.Display ?? localVersion ?? "?";
        Local = AppVersion.Parse(localVersion);
        _fetch = fetch;
        Settings = settings;
        _save = save;
        _log = log;
    }

    /// <summary>La versión de este PC; null si no se entiende (se trata como "dev").</summary>
    public AppVersion? Local { get; }

    /// <summary>Para mostrar: <c>5.0</c>, o el texto tal cual si no se entiende.</summary>
    public string LocalText { get; }

    public UpdateSettings Settings { get; private set; }

    /// <summary>Hay una versión de desarrollo (o ilegible): no avisa nunca.</summary>
    public bool IsDev => Local is not { IsDev: false };

    public event Action? Changed;

    /// <summary>La release que se ofrece, o null (no hay más nueva, la omitió o es una versión de desarrollo).</summary>
    public ReleaseInfo? Offer { get; private set; }

    /// <summary>La última lista descargada (vacía hasta la primera comprobación con éxito).</summary>
    public IReadOnlyList<ReleaseInfo> LastReleases => _releases;

    public bool AutoCheck
    {
        get => Settings.AutoCheck;
        set
        {
            if (Settings.AutoCheck == value) return;
            Update(Settings with { AutoCheck = value });
        }
    }

    /// <summary>
    /// Busca en GitHub. Un fallo de red no es un error: se anota en el log y devuelve <see cref="CheckOutcome.Failed"/>.
    /// <paramref name="manual"/> ("Buscar ahora"): si la versión más alta es la que se omitió, se deja de omitir y se ofrece otra vez.
    /// </summary>
    public async Task<CheckOutcome> CheckAsync(bool manual = false, CancellationToken cancellationToken = default)
    {
        IReadOnlyList<ReleaseInfo> list;
        try
        {
            list = await _fetch(cancellationToken); // sin ConfigureAwait(false): Changed debe dispararse en el hilo de quien llama (la interfaz)
        }
        catch (Exception ex) when (ex is HttpRequestException or FormatException or TaskCanceledException or IOException or InvalidOperationException)
        {
            if (cancellationToken.IsCancellationRequested) throw new OperationCanceledException(cancellationToken);
            _log?.Invoke($"No se pudo buscar actualizaciones: {ex.Message}");
            return CheckOutcome.Failed;
        }

        _releases = list;
        var latest = Releases.Highest(list);
        if (manual && latest is not null && UpdatePolicy.IsSkipped(latest.Version, Settings.SkippedVersion))
            Update(Settings with { SkippedVersion = null });
        var offer = UpdatePolicy.ShouldOffer(Local, latest, Settings.SkippedVersion) ? latest : null;
        Offer = offer;
        _log?.Invoke($"Buscar actualizaciones: instalada {LocalText}, última {latest?.Version.Display ?? "ninguna"}{(offer is null ? "" : " (se ofrece)")}");
        Changed?.Invoke();

        if (IsDev) return CheckOutcome.DevBuild;
        if (offer is not null) return CheckOutcome.Available;
        if (latest is not null && Local is { } mine && latest.Version > mine) return CheckOutcome.Skipped;
        return CheckOutcome.UpToDate;
    }

    /// <summary>"Omitir esta versión": no vuelve a avisar de la que se ofrece ahora, sí de la siguiente.</summary>
    public void SkipOffered()
    {
        if (Offer is not { } offer) return;
        Update(Settings with { SkippedVersion = offer.Version.Display });
        Offer = null;
        Changed?.Invoke();
    }

    /// <summary>
    /// ¿Hay que mostrar el globo de la bandeja? Solo la primera vez que se detecta cada versión; llamar a <see cref="MarkNotified"/> al mostrarlo.
    /// </summary>
    public bool ShouldNotify => Offer is { } offer && !offer.Version.Display.Equals(Settings.NotifiedVersion, StringComparison.Ordinal);

    public void MarkNotified()
    {
        if (Offer is { } offer) Update(Settings with { NotifiedVersion = offer.Version.Display });
    }

    /// <summary>La versión que dijo el móvil conectado por USB (<c>APP_VERSION</c>), o null si no hay móvil o no la envió.</summary>
    public void SetPhoneVersion(string? version)
    {
        if (_phoneVersion == version) return;
        _phoneVersion = version;
        Changed?.Invoke();
    }

    public string? PhoneVersion => _phoneVersion;

    /// <summary>Los avisos que hay que mostrar ahora, el de versión nueva primero. Como mucho dos.</summary>
    public IReadOnlyList<UpdateBanner> Banners
    {
        get
        {
            var banners = new List<UpdateBanner>();
            if (Offer is { } offer)
                banners.Add(new(BannerKind.NewVersion, UpdatePolicy.NewVersionText(offer), offer, CanInstall: true, CanSkip: true));

            var cross = UpdatePolicy.CrossDevice(VersionSide.Pc, Local?.Display, _phoneVersion);
            switch (cross.Kind)
            {
                // Si ya se ofrece una release (que es al menos tan alta como la del móvil), ese aviso lo cubre.
                case CrossDeviceKind.OtherNewer when Offer is null:
                    var target = Releases.Find(_releases, cross.OtherVersion!.Value);
                    banners.Add(new(BannerKind.PhoneNewer, cross.Message, target, CanInstall: target is not null, CanSkip: false));
                    break;
                case CrossDeviceKind.OtherOlder:
                    banners.Add(new(BannerKind.PhoneOlder, cross.Message, null, CanInstall: false, CanSkip: false));
                    break;
            }
            return banners;
        }
    }

    /// <summary>¿Hay algo que avisar (punto azul en la bandeja)? Los avisos informativos del móvil más antiguo no cuentan.</summary>
    public bool HasUpdate => Banners.Any(b => b.CanInstall);

    private void Update(UpdateSettings settings)
    {
        Settings = settings;
        try
        {
            _save(settings);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            _log?.Invoke($"No se pudo guardar updates.json: {ex.Message}");
        }
        Changed?.Invoke();
    }
}
