/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.ide.editor.orion.client.jso;

import com.google.gwt.core.client.JavaScriptObject;

/** Represent registry of annotation types */
public class OrionAnnotationTypeOverlay extends JavaScriptObject {
  protected OrionAnnotationTypeOverlay() {}

  /**
   * Creates an annotation of a given type with the specified start end end offsets.
   *
   * @param type The annotation type (for example, orion.annotation.error).
   * @param start The start offset of the annotation in the text model.
   * @param end The end offset of the annotation in the text model.
   * @param title The text description for the annotation if different then the type description.
   * @return the new annotation
   */
  public final native OrionAnnotationOverlay createAnnotation(
      String type, int start, int end, String title) /*-{
        return this.createAnnotation(type, start, end, title);
    }-*/;
}
