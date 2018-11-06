package picocli.model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import picocli.annots.Option;
import picocli.annots.Parameters;
import picocli.help.Help;
import picocli.util.ObjectUtilsExt;

/**
 * Helper class to reflectively create OptionSpec and PositionalParamSpec objects from
 * annotated elements. Package protected for testing. CONSIDER THIS CLASS PRIVATE.
 */
class ArgsReflection {
    static OptionSpec extractOptionSpec(TypedMember member, IFactory factory) {
        Option option = member.getAnnotation(Option.class);
        OptionSpec.Builder builder = OptionSpec.builder(option.names());
        initCommon(builder, member);

        builder.help(option.help());
        builder.usageHelp(option.usageHelp());
        builder.versionHelp(option.versionHelp());
        builder.showDefaultValue(option.showDefaultValue());
        if (!NoCompletionCandidates.class.equals(option.completionCandidates())) {
            builder.completionCandidates(Factory.createCompletionCandidates(factory,
                    option.completionCandidates()));
        }

        builder.arity(Range.optionArity(member));
        builder.required(option.required());
        builder.interactive(option.interactive());
        Class<?>[] elementTypes = inferTypes(member.getType(), option.type(),
                member.getGenericType());
        builder.auxiliaryTypes(elementTypes);
        builder.paramLabel(inferLabel(option.paramLabel(), member.name(), member.getType(),
                elementTypes));
        builder.hideParamSyntax(option.hideParamSyntax());
        builder.description(option.description());
        builder.descriptionKey(option.descriptionKey());
        builder.splitRegex(option.split());
        builder.hidden(option.hidden());
        builder.defaultValue(option.defaultValue());
        builder.converters(Factory.createConverter(factory, option.converter()));
        return builder.build();
    }

    static PositionalParamSpec extractPositionalParamSpec(TypedMember member,
            IFactory factory) {
        PositionalParamSpec.Builder builder = PositionalParamSpec.builder();
        initCommon(builder, member);
        Range arity = Range.parameterArity(member);
        builder.arity(arity);
        builder.index(Range.parameterIndex(member));
        builder.capacity(Range.parameterCapacity(member));
        builder.required(arity.min > 0);

        Parameters parameters = member.getAnnotation(Parameters.class);
        builder.interactive(parameters.interactive());
        Class<?>[] elementTypes = inferTypes(member.getType(), parameters.type(),
                member.getGenericType());
        builder.auxiliaryTypes(elementTypes);
        builder.paramLabel(inferLabel(parameters.paramLabel(), member.name(),
                member.getType(), elementTypes));
        builder.hideParamSyntax(parameters.hideParamSyntax());
        builder.description(parameters.description());
        builder.descriptionKey(parameters.descriptionKey());
        builder.splitRegex(parameters.split());
        builder.hidden(parameters.hidden());
        builder.defaultValue(parameters.defaultValue());
        builder.converters(Factory.createConverter(factory, parameters.converter()));
        builder.showDefaultValue(parameters.showDefaultValue());
        if (!NoCompletionCandidates.class.equals(parameters.completionCandidates())) {
            builder.completionCandidates(Factory.createCompletionCandidates(factory,
                    parameters.completionCandidates()));
        }
        return builder.build();
    }

    static PositionalParamSpec extractUnannotatedPositionalParamSpec(TypedMember member,
            IFactory factory) {
        PositionalParamSpec.Builder builder = PositionalParamSpec.builder();
        initCommon(builder, member);
        Range arity = Range.parameterArity(member);
        builder.arity(arity);
        builder.index(Range.parameterIndex(member));
        builder.capacity(Range.parameterCapacity(member));
        builder.required(arity.min > 0);

        builder.interactive(false);
        Class<?>[] elementTypes = inferTypes(member.getType(), new Class<?>[] {},
                member.getGenericType());
        builder.auxiliaryTypes(elementTypes);
        builder.paramLabel(inferLabel(null, member.name(), member.getType(), elementTypes));
        builder.hideParamSyntax(false);
        builder.description(new String[0]);
        builder.splitRegex("");
        builder.hidden(false);
        builder.defaultValue(null);
        builder.converters();
        builder.showDefaultValue(Help.Visibility.ON_DEMAND);
        return builder.build();
    }

    private static void initCommon(ArgSpec.Builder<?> builder, TypedMember member) {
        builder.type(member.getType());
        builder.withToString((member.accessible instanceof Field ? "field "
                : member.accessible instanceof Method ? "method "
                        : member.accessible.getClass().getSimpleName() + " ")
                + abbreviate(member.toGenericString()));

        builder.getter(member.getter()).setter(member.setter());
        builder.hasInitialValue(member.hasInitialValue);
        try {
            builder.initialValue(member.getter().get());
        } catch (Exception ex) {
            builder.initialValue(null);
        }
    }

    static String abbreviate(String text) {
        return text.replace("private ", "").replace("protected ", "").replace("public ", "")
                .replace("java.lang.", "");
    }

    private static String inferLabel(String label, String fieldName, Class<?> fieldType,
            Class<?>[] types) {
        if (!StringUtils.isBlank(label)) {
            return label.trim();
        }
        String name = fieldName;
        if (Map.class.isAssignableFrom(fieldType)) { // #195 better param labels for map fields
            Class<?>[] paramTypes = types;
            if (paramTypes.length < 2 || paramTypes[0] == null || paramTypes[1] == null) {
                name = "String=String";
            } else {
                name = paramTypes[0].getSimpleName() + "=" + paramTypes[1].getSimpleName();
            }
        }
        return "<" + name + ">";
    }

    static Class<?>[] inferTypes(Class<?> propertyType, Class<?>[] annotationTypes,
            Type genericType) {
        if (annotationTypes.length > 0) {
            return annotationTypes;
        }
        if (propertyType.isArray()) {
            return new Class<?>[] { propertyType.getComponentType() };
        }
        if (ObjectUtilsExt.isGroup(propertyType)) {
            if (genericType instanceof ParameterizedType) {// e.g. Map<Long, ? extends Number>
                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                Type[] paramTypes = parameterizedType.getActualTypeArguments(); // e.g. ? extends Number
                Class<?>[] result = new Class<?>[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    if (paramTypes[i] instanceof Class) {
                        result[i] = (Class<?>) paramTypes[i];
                        continue;
                    } // e.g. Long
                    if (paramTypes[i] instanceof WildcardType) { // e.g. ? extends Number
                        WildcardType wildcardType = (WildcardType) paramTypes[i];
                        Type[] lower = wildcardType.getLowerBounds(); // e.g. []
                        if (lower.length > 0 && lower[0] instanceof Class) {
                            result[i] = (Class<?>) lower[0];
                            continue;
                        }
                        Type[] upper = wildcardType.getUpperBounds(); // e.g. Number
                        if (upper.length > 0 && upper[0] instanceof Class) {
                            result[i] = (Class<?>) upper[0];
                            continue;
                        }
                    }
                    Arrays.fill(result, String.class);
                    return result; // too convoluted generic type, giving up
                }
                return result; // we inferred all types from ParameterizedType
            }
            return new Class<?>[] { String.class, String.class }; // field is multi-value but not ParameterizedType
        }
        return new Class<?>[] { propertyType }; // not a multi-value field
    }
}
