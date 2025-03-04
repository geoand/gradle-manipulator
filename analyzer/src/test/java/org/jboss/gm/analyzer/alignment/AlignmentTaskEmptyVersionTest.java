package org.jboss.gm.analyzer.alignment;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.testfixtures.ProjectBuilder;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

@RunWith(BMUnitRunner.class)
@BMUnitConfig(bmunitVerbose = true)
@BMRule(name = "handle-injectIntoNewInstance",
        targetClass = "org.gradle.api.internal.AbstractTask",
        targetMethod = "injectIntoNewInstance(ProjectInternal, TaskIdentity, Callable)",
        targetLocation = "AFTER INVOKE set",
        action = "RETURN null")
public class AlignmentTaskEmptyVersionTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    /**
     * We can't just create a new AlignmentTask as the base AbstractTask has checks to
     * prevent that. The <code>ThreadLocal&lt;TaskInfo&gt; NEXT_INSTANCE</code> is normally
     * set by AbstractTask.injectIntoNewInstance but that has a finally block that immediately
     * clears the value. The Byteman rule at the top bypasses the finally so we can create the dummy
     * task object.
     */
    @Before
    public void before() {
        final Class<? extends TaskInternal> taskType = Cast.uncheckedCast(DefaultTask.class);
        final ProjectInternal projectInternal = mock(ProjectInternal.class);
        when(projectInternal.getGradle()).thenReturn(mock(GradleInternal.class));
        when(projectInternal.getServices()).thenReturn(mock(ServiceRegistry.class));
        when(projectInternal.getObjects()).thenReturn(mock(ObjectFactory.class));

        AbstractTask.injectIntoNewInstance(projectInternal,
                TaskIdentity.create("DummyIdentity", taskType, projectInternal), null);
    }

    @Test
    public void testProject() throws Exception {
        final File simpleProjectRoot = tempDir.newFolder("simple-project");

        System.setProperty("ignoreUnresolvableDependencies", "true");

        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();

        p.apply(configuration -> configuration.plugin("base"));

        p.getRepositories().mavenCentral();
        p.getDependencies().add("default", "org.apache.commons:commons-configuration2:2.4");
        p.getDependencies().add("default", "org.apache.commons:dummy-artifact");

        AlignmentTask at = new AlignmentTask();

        // As getAllProjectDependencies is private, use reflection to modify the access control.
        Class[] types = new Class[1];
        types[0] = Project.class;
        Method m = at.getClass().getDeclaredMethod("getDependencies", Project.class, Set.class);
        m.setAccessible(true);
        HashMap<Dependency, ProjectVersionRef> result = (HashMap<Dependency, ProjectVersionRef>) m.invoke(at,
                new Object[] { p, new HashSet<ProjectVersionRef>() });
        Collection<ProjectVersionRef> allDependencies = result.values();

        assertEquals(1, allDependencies.size());
        assertEquals("org.apache.commons:commons-configuration2:2.4", allDependencies.stream().findFirst().get().toString());
    }
}
