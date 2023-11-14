package factorization.compat;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.Event;

public class CompatLoadEvent extends Event {
    public static class PreInit extends CompatLoadEvent {
        private final FMLPreInitializationEvent event;

        public PreInit(FMLPreInitializationEvent event) {
            this.event = event;
        }

        public FMLPreInitializationEvent getEvent() {
            return event;
        }
    }

    public static class Init extends CompatLoadEvent {
        private final FMLInitializationEvent event;

        public Init(FMLInitializationEvent event) {
            this.event = event;
        }

        public FMLInitializationEvent getEvent() {
            return event;
        }
    }

    public static class PostInit extends CompatLoadEvent {
        private final FMLPostInitializationEvent event;

        public PostInit(FMLPostInitializationEvent event) {
            this.event = event;
        }

        public FMLPostInitializationEvent getEvent() {
            return event;
        }
    }
}
