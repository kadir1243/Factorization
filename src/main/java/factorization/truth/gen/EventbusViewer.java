package factorization.truth.gen;

import com.google.common.base.Splitter;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.IEventListener;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import factorization.truth.api.IDocGenerator;
import factorization.truth.api.ITypesetter;
import factorization.truth.api.TruthError;
import factorization.truth.word.ClipboardWord;
import factorization.truth.word.TextWord;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class EventbusViewer implements IDocGenerator {
    @Override
    public void process(ITypesetter out, String arg) throws TruthError {
        if ("".equals(arg)) arg = null;
        inspectBus(out, MinecraftForge.EVENT_BUS, "Forge Event Bus", arg);
        inspectBus(out, FMLCommonHandler.instance().bus(), "FML Event Bus", arg);
        inspectBus(out, MinecraftForge.ORE_GEN_BUS, "Ore Gen Bus", arg);
        inspectBus(out, MinecraftForge.TERRAIN_GEN_BUS, "Terrain Gen Bus", arg);
    }

    void inspectBus(ITypesetter out, EventBus bus, String busName, String matchEvent) throws TruthError {
        ConcurrentHashMap<Object, ArrayList<IEventListener>> listeners = ReflectionHelper.getPrivateValue(EventBus.class, bus, "listeners");
        if (listeners == null) {
            out.write("Reflection failed!");
            return;
        }
        Set<Method> methodsSet = new HashSet<>();
        Set<Class<?>> eventTypesSet = new HashSet<>();
        for (Map.Entry<Object, ArrayList<IEventListener>> entry : listeners.entrySet()) {
            Object eventHandler = entry.getKey();
            for (Method method : eventHandler.getClass().getMethods()) {
                if (method.getAnnotation(SubscribeEvent.class) != null) {
                    methodsSet.add(method);
                    eventTypesSet.add(method.getParameterTypes()[0]);
                }
            }
        }
        List<Method> methods = new ArrayList<>(methodsSet);
        methods.sort(Comparator.comparing((Method o) -> o.getClass().getCanonicalName()).thenComparing(Method::getName));
        List<Class<?>> eventTypes = new ArrayList<>(eventTypesSet);
        eventTypes.sort(Comparator.comparing(Class::getCanonicalName));

        if (matchEvent != null) {
            boolean first = true;
            for (Method m : methodsSet) {
                String eventTypeName = m.getParameterTypes()[0].getCanonicalName();
                if (!matchEvent.equals(eventTypeName)) continue;
                if (first) {
                    out.write("\\newpage");
                    out.write("\\title{" + busName + "}\\nl");
                    outSplit(out, matchEvent, "cgi/eventbus/" + matchEvent);
                    out.write("\n\n");
                    first = false;
                }
                SubscribeEvent a = m.getAnnotation(SubscribeEvent.class);
                if (a.priority() == EventPriority.NORMAL && !a.receiveCanceled()) {
                    out.write("@SubscribeEvent\\nl");
                } else {
                    out.write(a.toString().replace("cpw.mods.fml.common.eventhandler.", "") + "\\nl");
                }
                final String handlerName = m.getDeclaringClass().getCanonicalName();
                outSplit(out, handlerName + "." + m.getName(), null);
                out.write(" [");
                out.write(new ClipboardWord("/scrap BusRemove " + handlerName + " " + m.getName()));
                out.write("]\n\n");
            }
        } else if (!eventTypes.isEmpty()) {
            out.write("\\newpage");
            out.write("\\title{Events: " + busName + "}\n\n");
            for (Class<?> eventType : eventTypes) {
                final String canonicalName = eventType.getCanonicalName();
                final String simpleName;
                if (eventType.isMemberClass()) {
                    Class<?> highest = eventType;
                    while (highest.getEnclosingClass() != null) {
                        highest = highest.getEnclosingClass();
                    }
                    String hc = highest.getCanonicalName();
                    int start = hc.length();
                    start -= highest.getSimpleName().length();
                    simpleName = canonicalName.substring(start);
                } else {
                    simpleName = eventType.getSimpleName();
                }
                outSplit(out, simpleName, "cgi/eventbus/" + canonicalName);
                out.write("\\nl");
            }
        }
    }

    Splitter split = Splitter.on(Pattern.compile("(?=[.=A-Z])"));

    void outSplit(ITypesetter out, String text, String link) {
        for (String t : split.split(text)) {
            final TextWord word = new TextWord(t);
            word.setLink(link);
            out.write(word);
        }
    }
}
