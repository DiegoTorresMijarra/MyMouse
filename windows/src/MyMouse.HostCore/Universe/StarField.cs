namespace MyMouse.HostCore.Universe;

/// <summary>
/// Una estrella del fondo "universo" (docs/spec-v4.7-universo.md §2). La posición va en coordenadas 0–1, para que no "salte" al
/// redimensionar. <see cref="Radius"/> en DIP, <see cref="Opacity"/> de 0 a 1. Si <see cref="Twinkle"/>, la opacidad oscila con
/// periodo <see cref="PeriodSeconds"/> y fase <see cref="Phase"/>.
/// </summary>
public readonly record struct Star(
    float X, float Y, float Radius, float Opacity, bool Halo, bool Twinkle, float PeriodSeconds, float Phase);

/// <summary>
/// Generador de estrellas con semilla. El mismo algoritmo (mulberry32) está en <c>dev.mymouse.core.universe.StarField</c> (móvil), así
/// que el universo se ve igual en el PC y en el móvil y los tests de las dos plataformas comparten los valores de referencia.
/// </summary>
public static class StarField
{
    public const int DefaultSeed = 0x4D794D73;

    /// <summary>Estrellas de referencia en un área de 400 × 600.</summary>
    public const int ReferenceCount = 90;
    public const double ReferenceArea = 400.0 * 600.0;

    public const int MinCount = 12;
    public const int MaxCount = 220;

    /// <summary>Una de cada 7 estrellas parpadea (unas 12–13 de cada 90).</summary>
    public const int TwinkleEvery = 7;

    public const float MinRadius = 0.5f;
    public const float MaxRadius = 1.6f;
    public const float MinOpacity = 0.35f;
    public const float MaxOpacity = 1f;
    public const double HaloChance = 0.10;
    public const float MinPeriod = 2f;
    public const float MaxPeriod = 5f;

    /// <summary>Cuánto baja, como mínimo, la opacidad de una estrella que parpadea (fracción de la suya).</summary>
    public const float TwinkleFloor = 0.35f;

    /// <summary>Cuántas estrellas caben en un área de <paramref name="width"/> × <paramref name="height"/> DIP: unas 90 por cada 400 × 600.</summary>
    public static int CountFor(double width, double height)
    {
        if (width <= 0 || height <= 0) return MinCount;
        var count = (int)Math.Round(ReferenceCount * (width * height) / ReferenceArea, MidpointRounding.AwayFromZero);
        return Math.Clamp(count, MinCount, MaxCount);
    }

    /// <summary>
    /// Las primeras <paramref name="count"/> estrellas de la secuencia de <paramref name="seed"/>. Determinista: con la misma semilla,
    /// <c>Generate(n)</c> es un prefijo de <c>Generate(m)</c> para n ≤ m.
    /// </summary>
    public static IReadOnlyList<Star> Generate(int count, int seed = DefaultSeed)
    {
        var rng = new Mulberry32(unchecked((uint)seed));
        var stars = new Star[Math.Max(0, count)];
        for (var i = 0; i < stars.Length; i++)
        {
            // Se consumen siempre los mismos números por estrella, para que la secuencia no dependa de los resultados.
            var x = rng.Next();
            var y = rng.Next();
            var radius = MinRadius + rng.Next() * (MaxRadius - MinRadius);
            var opacity = MinOpacity + rng.Next() * (MaxOpacity - MinOpacity);
            var halo = rng.Next() < HaloChance;
            var period = MinPeriod + rng.Next() * (MaxPeriod - MinPeriod);
            var phase = rng.Next() * (2 * Math.PI);
            stars[i] = new Star((float)x, (float)y, (float)radius, (float)opacity, halo, i % TwinkleEvery == 3, (float)period, (float)phase);
        }
        return stars;
    }

    /// <summary>
    /// Opacidad de <paramref name="star"/> en el instante <paramref name="seconds"/>. Sin parpadeo es la suya; con parpadeo oscila entre
    /// <see cref="TwinkleFloor"/> × su opacidad y su opacidad, con una senoide de periodo <see cref="Star.PeriodSeconds"/> y fase <see cref="Star.Phase"/>.
    /// </summary>
    public static float OpacityAt(Star star, double seconds)
    {
        if (!star.Twinkle) return star.Opacity;
        var wave = 0.5 + 0.5 * Math.Sin(2 * Math.PI * seconds / star.PeriodSeconds + star.Phase);
        return (float)(star.Opacity * (TwinkleFloor + (1 - TwinkleFloor) * wave));
    }

    /// <summary>mulberry32: generador de 32 bits pequeño y con buena distribución; devuelve números en [0, 1).</summary>
    private sealed class Mulberry32(uint seed)
    {
        private uint _a = seed;

        public double Next()
        {
            unchecked
            {
                _a += 0x6D2B79F5;
                var t = _a;
                t = (t ^ (t >> 15)) * (t | 1);
                t ^= t + (t ^ (t >> 7)) * (t | 61);
                return (t ^ (t >> 14)) / 4294967296.0;
            }
        }
    }
}
