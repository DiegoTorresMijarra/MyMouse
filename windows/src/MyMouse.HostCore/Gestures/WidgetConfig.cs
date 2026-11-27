using System.Text;
using System.Text.Json;
using MyMouse.Protocol;

namespace MyMouse.HostCore.Gestures;

/// <summary>Un widget del panel del móvil. Ver docs/spec-v2-widgets.md §3.</summary>
public abstract record WidgetDefinition
{
    public abstract WidgetKind Kind { get; }
}

/// <summary>Botón de un toque: ejecuta una acción (las mismas que las formas).</summary>
public sealed record ButtonWidget(string Label, string Icon, GestureAction Action) : WidgetDefinition
{
    public override WidgetKind Kind => WidgetKind.Button;
}

/// <summary>Control multimedia con lo que suena.</summary>
public sealed record MediaWidget : WidgetDefinition
{
    public override WidgetKind Kind => WidgetKind.Media;
}

/// <summary>Volumen del sistema.</summary>
public sealed record VolumeWidget : WidgetDefinition
{
    public override WidgetKind Kind => WidgetKind.Volume;
}

/// <summary>Lee la lista <c>"widgets"</c> de <c>gestures.json</c>. Lo mal escrito se descarta con un aviso, como en las reglas de formas.</summary>
internal static class WidgetParser
{
    public const int MaxLabelChars = 40;
    public const int MaxIconBytes = 8;

    public static IReadOnlyList<WidgetDefinition> Parse(JsonElement list, List<string> warnings)
    {
        if (list.ValueKind != JsonValueKind.Array)
        {
            warnings.Add("\"widgets\" debe ser una lista: [ { \"type\": \"media\" }, ... ]");
            return [];
        }

        var widgets = new List<WidgetDefinition>();
        var hasMedia = false;
        var hasVolume = false;
        var index = 0;
        foreach (var item in list.EnumerateArray())
        {
            index++;
            if (widgets.Count >= ProtocolInfo.MaxWidgets)
            {
                warnings.Add($"Widget {index}: se ignoran los widgets a partir del {ProtocolInfo.MaxWidgets + 1} (máximo {ProtocolInfo.MaxWidgets})");
                break;
            }
            try
            {
                var widget = ParseWidget(item);
                switch (widget)
                {
                    case MediaWidget when hasMedia:
                        throw new FormatException("ya hay un widget \"media\" (solo se admite uno)");
                    case VolumeWidget when hasVolume:
                        throw new FormatException("ya hay un widget \"volume\" (solo se admite uno)");
                    case MediaWidget:
                        hasMedia = true;
                        break;
                    case VolumeWidget:
                        hasVolume = true;
                        break;
                }
                widgets.Add(widget);
            }
            catch (Exception ex) when (ex is FormatException or InvalidOperationException or KeyNotFoundException)
            {
                warnings.Add($"Widget {index}: {ex.Message}");
            }
        }
        return widgets;
    }

    private static WidgetDefinition ParseWidget(JsonElement e)
    {
        if (e.ValueKind != JsonValueKind.Object)
            throw new FormatException("debe ser un objeto { \"type\": ... }");
        var type = GestureConfig.RequiredString(e, "type");
        switch (type.ToLowerInvariant())
        {
            case "media":
                return new MediaWidget();
            case "volume":
                return new VolumeWidget();
            case "button":
            {
                var label = GestureConfig.RequiredString(e, "label").Trim();
                if (label.Length > MaxLabelChars)
                    throw new FormatException($"la etiqueta de {label.Length} caracteres supera el máximo ({MaxLabelChars})");
                var icon = GestureConfig.OptionalString(e, "icon")?.Trim() ?? "";
                if (Encoding.UTF8.GetByteCount(icon) > MaxIconBytes)
                    throw new FormatException($"'{label}': el icono supera los {MaxIconBytes} bytes (un emoji o 1–3 letras)");
                if (!e.TryGetProperty("action", out var action) || action.ValueKind != JsonValueKind.Object)
                    throw new FormatException($"'{label}' no tiene \"action\"");
                return new ButtonWidget(label, icon, GestureConfig.ParseAction(action));
            }
            default:
                throw new FormatException($"Tipo de widget desconocido: '{type}' (válidos: button, media, volume)");
        }
    }
}
