package factorization.api.wind;

import java.util.HashMap;
import java.util.Map;

public class WindModel {
    private static final IWindModel default_model = new DefaultWindModel();
    public static IWindModel activeModel = default_model;
    public static final String userModelChoice = readConfig();
    public static final Map<String, IWindModel> models = new HashMap<>();
    private static int best = -1;

    /**
     * Registers a wind model. Might change activeModel, but of course this should be done during load time.
     * @param modelName The name of the model to use; suggested to use the modid of the register.
     * @param model The IWindModel instance
     * @param awesomeness A self-evaluation of how cool the model is.
     */
    public static void register(String modelName, IWindModel model, int awesomeness) {
        models.put(modelName, model);
        if (modelName.equalsIgnoreCase(userModelChoice)) {
            awesomeness = Integer.MAX_VALUE;
        }
        if (awesomeness > best) {
            best = awesomeness;
            activeModel = model;
        }
    }

    private static String readConfig() {
        // TODO: Load from a config file
        return "";
    }

    static {
        register("default", default_model, 0);
    }

}
