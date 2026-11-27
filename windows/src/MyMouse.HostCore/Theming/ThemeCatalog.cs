using System.Globalization;
using System.Reflection;
using System.Text.Json;

namespace MyMouse.HostCore.Theming;

/// <summary>Una nebulosa: degradado radial con centro (X, Y) relativo al ancho y al alto, y radio R relativo al ancho. Colores como 0xRRGGBB.</summary>
public sealed record Nebula(int Color, float Opacity, float X, float Y, float R);

/// <summary>
/// Un tema (docs/spec-v4.8-temas.md §2): 1–3 colores de base (degradado diagonal), 0–3 nebulosas, estrellas (con multiplicador de
/// densidad y profundidad de parpadeo) y el acento por defecto.
/// </summary>
public sealed record ThemeDef(
    string Id, string Name, IReadOnlyList<int> Base, IReadOnlyList<Nebula> Nebulas, bool Stars, float StarDensity, float TwinkleDepth, int Accent)
{
    /// <summary>El color medio de la base: el de los fondos sólidos (diálogos, menús).</summary>
    public int BaseMid => Base[Base.Count / 2];
}

/// <summary>Un acento que se puede elegir en lugar del del tema.</summary>
public sealed record AccentOption(string Id, string Name, int Color);

/// <summary>Lo que el usuario ha elegido en el PC: tema, acento y estrellas. Se guarda en <c>toolbox.json</c>; no se sincroniza con el móvil.</summary>
public sealed record ThemeChoice(
    string ThemeId = ThemeCatalog.DefaultId,
    string AccentId = ThemeCatalog.AccentAuto,
    bool Stars = true,
    bool Twinkle = true)
{
    /// <summary>El tema elegido; un id desconocido da el de por defecto.</summary>
    public ThemeDef Theme => ThemeCatalog.Resolve(ThemeId);

    /// <summary>El acento resuelto: el elegido o, en Automático, el del tema.</summary>
    public int Accent => ThemeCatalog.AccentColor(Theme, AccentId);

    /// <summary>¿Se dibujan estrellas? Hace falta que el tema las tenga y que la casilla esté marcada.</summary>
    public bool ShowStars => Theme.Stars && Stars;

    /// <summary>¿Parpadean? Con estrellas, un tema que parpadee y la animación activada.</summary>
    public bool Twinkles => ShowStars && Theme.TwinkleDepth > 0 && Twinkle;

    /// <summary>Lo desconocido pasa a Universo y a Automático.</summary>
    public ThemeChoice Normalized() => this with
    {
        ThemeId = ThemeCatalog.Find(ThemeId)?.Id ?? ThemeCatalog.DefaultId,
        AccentId = ThemeCatalog.FindAccent(AccentId) is not null ? AccentId : ThemeCatalog.AccentAuto,
    };
}

/// <summary>Lo que dice <c>shared/themes.json</c>.</summary>
public sealed record ThemeFile(string DefaultTheme, IReadOnlyList<AccentOption> Accents, IReadOnlyList<ThemeDef> Themes);

/// <summary>
/// Los temas de MyMouse. Se leen de <c>shared/themes.json</c> (incrustado como recurso), la misma fuente que el móvil replica en
/// <c>dev.mymouse.core.theme.Themes</c> y compara con un test. Lógica pura, sin WPF.
/// </summary>
public static class ThemeCatalog
{
    public const string DefaultId = "universe";

    /// <summary>Id del "acento automático": el del tema.</summary>
    public const string AccentAuto = "auto";

    private const string ResourceName = "MyMouse.HostCore.themes.json";

    private static readonly Lazy<ThemeFile> Loaded = new(LoadEmbedded);

    public static ThemeFile File => Loaded.Value;

    public static IReadOnlyList<ThemeDef> All => File.Themes;

    public static IReadOnlyList<AccentOption> Accents => File.Accents;

    public static ThemeDef? Find(string? id) => All.FirstOrDefault(t => t.Id == id);

    /// <summary>El tema de <paramref name="id"/>; si no existe (o es null, como en un <c>toolbox.json</c> sin tema), Universo.</summary>
    public static ThemeDef Resolve(string? id) => Find(id) ?? Find(DefaultId)!;

    public static AccentOption? FindAccent(string? id) => Accents.FirstOrDefault(a => a.Id == id);

    /// <summary>El acento de <paramref name="accentId"/> sobre <paramref name="theme"/>: "auto" (o desconocido) es el del tema.</summary>
    public static int AccentColor(ThemeDef theme, string? accentId) => FindAccent(accentId)?.Color ?? theme.Accent;

    // ---- Colores derivados (los mismos que en Themes.kt) ----

    /// <summary>Mezcla <paramref name="a"/> con <paramref name="b"/>: 0 = a, 1 = b.</summary>
    public static int Mix(int a, int b, float t)
    {
        static int Ch(int a, int b, int shift, float t)
        {
            var ca = (a >> shift) & 0xFF;
            var cb = (b >> shift) & 0xFF;
            return Math.Clamp((int)(ca + (cb - ca) * t), 0, 255);
        }
        return (Ch(a, b, 16, t) << 16) | (Ch(a, b, 8, t) << 8) | Ch(a, b, 0, t);
    }

    /// <summary>Principio del degradado de los botones primarios: el acento oscurecido, para que el texto blanco se lea con cualquier acento.</summary>
    public static int AccentStart(int accent) => Mix(accent, 0x000000, 0.35f);

    public static int AccentEnd(int accent) => Mix(accent, 0x000000, 0.55f);

    /// <summary>Fondo sólido de menús y diálogos: el color medio de la base, aclarado un poco.</summary>
    public static int SurfaceSolid(ThemeDef theme) => Mix(theme.BaseMid, 0xFFFFFF, 0.07f);

    /// <summary>Cuántas estrellas pide el tema en un área (0 si no tiene).</summary>
    public static int StarCount(ThemeDef theme, double width, double height) =>
        theme.Stars ? Universe.StarField.CountFor(width, height, theme.StarDensity) : 0;

    public static string Hex(int rgb) => "#" + (rgb & 0xFFFFFF).ToString("X6", CultureInfo.InvariantCulture);

    public static int? ParseHex(string? text)
    {
        if (text is null || text.Length != 7 || text[0] != '#') return null;
        return int.TryParse(text.AsSpan(1), NumberStyles.AllowHexSpecifier, CultureInfo.InvariantCulture, out var v) ? v : null;
    }

    // ---- Lectura y validación ----

    /// <summary>Lee y valida un <c>themes.json</c>. Lanza <see cref="FormatException"/> con un mensaje en español si no es válido.</summary>
    public static ThemeFile Parse(string json)
    {
        JsonDocument doc;
        try
        {
            doc = JsonDocument.Parse(json);
        }
        catch (JsonException ex)
        {
            throw new FormatException($"themes.json: no es JSON válido ({ex.Message})");
        }
        using (doc)
        {
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object) throw Fail("la raíz debe ser un objeto");
            var accents = Array(root, "accents").Select(a => new AccentOption(Str(a, "id"), Str(a, "name"), Color(a, "color"))).ToList();
            var themes = Array(root, "themes").Select(t => new ThemeDef(
                Str(t, "id"), Str(t, "name"),
                Array(t, "base").Select(c => ParseHex(c.ValueKind == JsonValueKind.String ? c.GetString() : null) ?? throw Fail($"color base no válido: {c}")).ToList(),
                Array(t, "nebulas").Select(n => new Nebula(Color(n, "color"), Num(n, "opacity"), Num(n, "x"), Num(n, "y"), Num(n, "r"))).ToList(),
                Bool(t, "stars"), Num(t, "starDensity"), Num(t, "twinkleDepth"), Color(t, "accent"))).ToList();
            var file = new ThemeFile(Str(root, "defaultTheme"), accents, themes);
            Validate(file);
            return file;
        }
    }

    /// <summary>Reglas: ids únicos y no vacíos, 1–3 colores de base, 0–3 nebulosas con opacidad 0–1 y radio &gt; 0, el tema por defecto existe.</summary>
    public static void Validate(ThemeFile file)
    {
        if (file.Themes.Count == 0) throw Fail("no hay temas");
        if (file.Themes.Select(t => t.Id).Distinct().Count() != file.Themes.Count) throw Fail("ids de tema repetidos");
        if (file.Themes.Any(t => string.IsNullOrWhiteSpace(t.Id))) throw Fail("hay un tema sin id");
        if (file.Themes.All(t => t.Id != file.DefaultTheme)) throw Fail($"el tema por defecto '{file.DefaultTheme}' no existe");
        var accentIds = file.Accents.Select(a => a.Id).ToList();
        if (accentIds.Distinct().Count() != accentIds.Count || accentIds.Contains(AccentAuto)) throw Fail("ids de acento repetidos o reservados");
        foreach (var t in file.Themes)
        {
            if (t.Base.Count is < 1 or > 3) throw Fail($"{t.Id}: la base lleva de 1 a 3 colores");
            if (t.Nebulas.Count > 3) throw Fail($"{t.Id}: como mucho 3 nebulosas");
            foreach (var n in t.Nebulas)
            {
                if (n.Opacity is < 0 or > 1) throw Fail($"{t.Id}: opacidad de nebulosa fuera de 0–1");
                if (n.R <= 0) throw Fail($"{t.Id}: radio de nebulosa no positivo");
            }
            if (t.StarDensity < 0 || t.TwinkleDepth is < 0 or > 1) throw Fail($"{t.Id}: estrellas no válidas");
            if (t.Stars && t.StarDensity <= 0) throw Fail($"{t.Id}: tiene estrellas pero densidad 0");
        }
    }

    private static FormatException Fail(string message) => new($"themes.json: {message}");

    private static JsonElement Prop(JsonElement o, string key) =>
        o.ValueKind == JsonValueKind.Object && o.TryGetProperty(key, out var v) ? v : throw Fail($"falta '{key}'");

    private static IEnumerable<JsonElement> Array(JsonElement o, string key)
    {
        var v = Prop(o, key);
        return v.ValueKind == JsonValueKind.Array ? v.EnumerateArray().ToList() : throw Fail($"'{key}' debe ser una lista");
    }

    private static string Str(JsonElement o, string key)
    {
        var v = Prop(o, key);
        return v.ValueKind == JsonValueKind.String ? v.GetString()! : throw Fail($"'{key}' debe ser texto");
    }

    private static float Num(JsonElement o, string key)
    {
        var v = Prop(o, key);
        return v.ValueKind == JsonValueKind.Number ? v.GetSingle() : throw Fail($"'{key}' debe ser un número");
    }

    private static bool Bool(JsonElement o, string key)
    {
        var v = Prop(o, key);
        return v.ValueKind is JsonValueKind.True or JsonValueKind.False ? v.GetBoolean() : throw Fail($"'{key}' debe ser verdadero o falso");
    }

    private static int Color(JsonElement o, string key) => ParseHex(Str(o, key)) ?? throw Fail($"color no válido en '{key}'");

    private static ThemeFile LoadEmbedded()
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(ResourceName)
            ?? throw new InvalidOperationException($"Falta el recurso incrustado {ResourceName} (shared/themes.json).");
        using var reader = new StreamReader(stream, System.Text.Encoding.UTF8);
        return Parse(reader.ReadToEnd());
    }
}
