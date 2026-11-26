using System.Text.Encodings.Web;
using System.Text.Json;

namespace MyMouse.HostCore.Gestures;

/// <summary>Escribe <c>gestures.json</c> con formato limpio, en el mismo formato que lee <see cref="GestureConfig.Parse"/>.</summary>
internal static class GestureConfigWriter
{
    public static string Write(GestureConfig config)
    {
        using var stream = new MemoryStream();
        using (var w = new Utf8JsonWriter(stream, new JsonWriterOptions
        {
            Indented = true,
            Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping, // sin \u002B por el "+" de los atajos ni acentos escapados
        }))
        {
            w.WriteStartObject();
            w.WriteStartArray("bindings");
            foreach (var b in config.Bindings)
            {
                w.WriteStartObject();
                w.WriteString("gesture", b.Gesture);
                w.WritePropertyName("action");
                WriteAction(w, b.Action);
                if (!string.IsNullOrWhiteSpace(b.Help)) w.WriteString("help", b.Help);
                if (b.Pinned) w.WriteBoolean("pin", true);
                if (!string.IsNullOrWhiteSpace(b.App)) w.WriteString("app", b.App);
                w.WriteEndObject();
            }
            w.WriteEndArray();

            if (config.HasWidgetsSection || config.Widgets.Count > 0)
            {
                w.WriteStartArray("widgets");
                foreach (var widget in config.Widgets)
                {
                    w.WriteStartObject();
                    switch (widget)
                    {
                        case MediaWidget:
                            w.WriteString("type", "media");
                            break;
                        case VolumeWidget:
                            w.WriteString("type", "volume");
                            break;
                        case ButtonWidget b:
                            w.WriteString("type", "button");
                            w.WriteString("label", b.Label);
                            if (!string.IsNullOrEmpty(b.Icon)) w.WriteString("icon", b.Icon);
                            w.WritePropertyName("action");
                            WriteAction(w, b.Action);
                            break;
                        default:
                            throw new NotSupportedException(widget.GetType().Name);
                    }
                    w.WriteEndObject();
                }
                w.WriteEndArray();
            }
            w.WriteEndObject();
        }
        return new System.Text.UTF8Encoding(false).GetString(stream.ToArray()) + Environment.NewLine;
    }

    private static void WriteAction(Utf8JsonWriter w, GestureAction action)
    {
        w.WriteStartObject();
        switch (action)
        {
            case KeysAction k:
                w.WriteString("type", "keys");
                w.WriteString("keys", k.Text);
                break;
            case LaunchAction l:
                w.WriteString("type", "launch");
                w.WriteString("target", l.Target);
                if (!string.IsNullOrWhiteSpace(l.Arguments)) w.WriteString("args", l.Arguments);
                if (l.FocusIfRunning) w.WriteBoolean("focusIfRunning", true);
                break;
            case TextAction t:
                w.WriteString("type", "text");
                w.WriteString("text", t.Text);
                break;
            case MediaAction m:
                w.WriteString("type", "media");
                w.WriteString("key", m.Key);
                break;
            case MyMouseAction { Command: MyMouseCommand.Pause }:
                w.WriteString("type", "mymouse");
                w.WriteString("command", "pause");
                break;
            case MyMouseAction { Command: MyMouseCommand.Lock }:
                w.WriteString("type", "mymouse");
                w.WriteString("command", "lock");
                break;
            default:
                throw new NotSupportedException(action.GetType().Name);
        }
        w.WriteEndObject();
    }
}
