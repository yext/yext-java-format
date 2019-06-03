/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.googlejavaformat.intellij;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.BoundType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.intellij.openapi.util.TextRange;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class FormatterUtil {

  private FormatterUtil() {}

  static Map<TextRange, String> getReplacements(String text, Collection<TextRange> ranges) {
    try {
      ImmutableMap.Builder<TextRange, String> replacements = ImmutableMap.builder();

      String jarPath =
          String.format("%s/devops/format/yext-java-formatter.jar", System.getenv("ALPHA"));
      File formatterJar = new File(jarPath);
      ClassLoader classLoader =
          URLClassLoader.newInstance(new URL[] {formatterJar.toURI().toURL()});

      Class<?> formatterClass =
          Class.forName("com.google.googlejavaformat.java.Formatter", true, classLoader);
      Class<?> replacementClass =
          Class.forName("com.google.googlejavaformat.java.Replacement", true, classLoader);
      Class<?> rangeClass = Class.forName("com.google.common.collect.Range", true, classLoader);

      Object formatter = formatterClass.newInstance();
      Method getFormatReplacements =
          formatterClass.getMethod("getFormatReplacements", String.class, Collection.class);
      Object replacementsList =
          getFormatReplacements.invoke(formatter, text, toRanges(ranges, classLoader));
      for (Object replacement : (Iterable) replacementsList) {
        // The formatter jar contains it's own copy of the Range class, resulting in
        // duplicate but not equal classes. Therefore the Range objects returned from
        // methods invoked by the class loader will need to be cast to the correct class.
        Object rangeObj = replacementClass.getMethod("getReplaceRange").invoke(replacement);
        Range<Integer> range =
            Range.closedOpen(
                (Integer) rangeClass.getMethod("lowerEndpoint").invoke(rangeObj),
                (Integer) rangeClass.getMethod("upperEndpoint").invoke(rangeObj));

        replacements.put(
            toTextRange(range),
            (String) replacementClass.getMethod("getReplacementString").invoke(replacement));
      }
      return replacements.build();
    } catch (InvocationTargetException
        | MalformedURLException
        | ClassNotFoundException
        | IllegalAccessException
        | InstantiationException
        | NoSuchMethodException e) {
      return ImmutableMap.of();
    }
  }

  private static Collection<Object> toRanges(Collection<TextRange> textRanges, ClassLoader loader)
      throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
          IllegalAccessException {
    Class<?> rangeClass = Class.forName("com.google.common.collect.Range", true, loader);
    Method closedOpen = rangeClass.getMethod("closedOpen", Comparable.class, Comparable.class);

    List<Object> ranges = new ArrayList<>();

    for (TextRange textRange : textRanges) {
      ranges.add(closedOpen.invoke(null, textRange.getStartOffset(), textRange.getEndOffset()));
    }

    return ranges;
  }

  private static TextRange toTextRange(Range<Integer> range) {
    checkState(
        range.lowerBoundType().equals(BoundType.CLOSED)
            && range.upperBoundType().equals(BoundType.OPEN));
    return new TextRange(range.lowerEndpoint(), range.upperEndpoint());
  }
}
