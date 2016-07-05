/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.platform.runner;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.platform.commons.util.StringUtils;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.JavaClassSource;
import org.junit.platform.engine.support.descriptor.JavaMethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

/**
 * @since 1.0
 */
class JUnitPlatformTestTree {

	private final Map<TestIdentifier, Description> descriptions = new HashMap<>();
	private final TestPlan plan;
	private final Description suiteDescription;

	JUnitPlatformTestTree(TestPlan plan, Class<?> testClass) {
		this.plan = plan;
		this.suiteDescription = generateSuiteDescription(plan, testClass);
	}

	Description getSuiteDescription() {
		return this.suiteDescription;
	}

	Description getDescription(TestIdentifier identifier) {
		return this.descriptions.get(identifier);
	}

	private Description generateSuiteDescription(TestPlan testPlan, Class<?> testClass) {
		Description suiteDescription = Description.createSuiteDescription(testClass.getName());
		buildDescriptionTree(suiteDescription, testPlan);
		return suiteDescription;
	}

	private void buildDescriptionTree(Description suiteDescription, TestPlan testPlan) {
		testPlan.getRoots().forEach(testIdentifier -> buildDescription(testIdentifier, suiteDescription, testPlan));
	}

	void addDynamicDescription(TestIdentifier newIdentifier, String parentId) {
		Description parent = getDescription(this.plan.getTestIdentifier(parentId));
		this.plan.add(newIdentifier);
		buildDescription(newIdentifier, parent, this.plan);
	}

	private void buildDescription(TestIdentifier identifier, Description parent, TestPlan testPlan) {
		Description newDescription = createJUnit4Description(identifier, testPlan);
		parent.addChild(newDescription);
		this.descriptions.put(identifier, newDescription);
		testPlan.getChildren(identifier).forEach(
			testIdentifier -> buildDescription(testIdentifier, newDescription, testPlan));
	}

	private Description createJUnit4Description(TestIdentifier identifier, TestPlan testPlan) {
		if (identifier.isTest()) {
			String className = getClassName(identifier, testPlan);
			String name = getName(identifier);
			return Description.createTestDescription(className, name, identifier.getUniqueId());
		}
		else {
			return Description.createSuiteDescription(identifier.getDisplayName(), identifier.getUniqueId());
		}
	}

	private String getName(TestIdentifier testIdentifier) {
		Optional<TestSource> optionalSource = testIdentifier.getSource();
		if (optionalSource.isPresent()) {
			TestSource source = optionalSource.get();
			if (source instanceof JavaClassSource) {
				return ((JavaClassSource) source).getJavaClass().getName();
			}
			else if (source instanceof JavaMethodSource) {
				JavaMethodSource javaMethodSource = (JavaMethodSource) source;
				List<Class<?>> parameterTypes = javaMethodSource.getJavaMethodParameterTypes();
				if (parameterTypes.size() == 0) {
					return javaMethodSource.getJavaMethodName();
				}
				else {
					return String.format("%s(%s)", javaMethodSource.getJavaMethodName(), StringUtils.nullSafeToString(
						Class::getName, parameterTypes.toArray(new Class<?>[parameterTypes.size()])));
				}
			}
		}

		// Else fall back to display name
		return testIdentifier.getDisplayName();
	}

	private String getClassName(TestIdentifier testIdentifier, TestPlan testPlan) {
		Optional<TestSource> optionalSource = testIdentifier.getSource();
		if (optionalSource.isPresent()) {
			TestSource source = optionalSource.get();
			if (source instanceof JavaClassSource) {
				return ((JavaClassSource) source).getJavaClass().getName();
			}
			else if (source instanceof JavaMethodSource) {
				return ((JavaMethodSource) source).getJavaClass().getName();
			}
		}

		// Else fall back to display name of parent
		// @formatter:off
		return testPlan.getParent(testIdentifier)
				.map(TestIdentifier::getDisplayName)
				.orElse("<unrooted>");
		// @formatter:on
	}

	Set<TestIdentifier> getTestsInSubtree(TestIdentifier ancestor) {
		// @formatter:off
		return plan.getDescendants(ancestor).stream()
				.filter(TestIdentifier::isTest)
				.collect(toCollection(LinkedHashSet::new));
		// @formatter:on
	}

	Set<TestIdentifier> getFilteredLeaves(Filter filter) {
		Set<TestIdentifier> identifiers = applyFilterToDescriptions(filter);
		return removeNonLeafIdentifiers(identifiers);
	}

	private Set<TestIdentifier> removeNonLeafIdentifiers(Set<TestIdentifier> identifiers) {
		return identifiers.stream().filter(isALeaf(identifiers)).collect(toSet());
	}

	private Predicate<? super TestIdentifier> isALeaf(Set<TestIdentifier> identifiers) {
		return testIdentifier -> {
			Set<TestIdentifier> descendants = plan.getDescendants(testIdentifier);
			return identifiers.stream().noneMatch(descendants::contains);
		};
	}

	private Set<TestIdentifier> applyFilterToDescriptions(Filter filter) {
		// @formatter:off
		return descriptions.entrySet()
				.stream()
				.filter(entry -> filter.shouldRun(entry.getValue()))
				.map(Entry::getKey)
				.collect(toSet());
		// @formatter:on
	}

}
