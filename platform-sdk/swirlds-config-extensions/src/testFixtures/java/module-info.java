open module com.swirlds.config.extensions.test.fixtures {
    exports com.swirlds.config.extensions.test.fixtures;

    requires transitive com.swirlds.config.api;
    requires com.swirlds.common;
    requires com.swirlds.config.extensions;
    requires io.github.classgraph;
    requires static com.github.spotbugs.annotations;
}
