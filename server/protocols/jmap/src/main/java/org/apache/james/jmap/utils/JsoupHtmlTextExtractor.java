/****************************************************************
 O * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.utils;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsoupHtmlTextExtractor implements HtmlTextExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsoupHtmlTextExtractor.class);
    public static final String BR_TAG = "br";
    public static final String UL_TAG = "ul";
    public static final String OL_TAG = "ol";
    public static final String LI_TAG = "li";
    public static final String P_TAG = "p";
    public static final String IMG_TAG = "img";
    public static final String ALT_TAG = "alt";
    public static final int INITIAL_LIST_NESTED_LEVEL = 0;

    @Override
    public String toPlainText(String html) {
        try {
            Document document = Jsoup.parse(html);

            Element body = Optional.ofNullable(document.body()).orElse(document);

            return flatten(body, INITIAL_LIST_NESTED_LEVEL)
                .map(this::convertNodeToText)
                .reduce("", (s1, s2) -> s1 + s2);
        } catch (Exception e) {
            LOGGER.warn("Failed extracting text from html", e);
            return html;
        }
    }

    private String convertNodeToText(HTMLNode htmlNode) {
        Node node = htmlNode.underlyingNode;
        if (node instanceof TextNode) {
            TextNode textNode = (TextNode) node;
            return textNode.getWholeText();
        }
        if (node instanceof Element) {
            Element element = (Element) node;
            if (element.tagName().equals(BR_TAG)) {
                return "\n";
            }
            if (isList(element)) {
                return convertListElement(htmlNode.listNestedLevel);
            }
            if (element.tagName().equals(OL_TAG)) {
                return "\n\n";
            }
            if (element.tagName().equals(LI_TAG)) {
                return "\n" + StringUtils.repeat(" ", htmlNode.listNestedLevel) + "- ";
            }
            if (element.tagName().equals(P_TAG)) {
                return "\n\n";
            }
            if (element.tagName().equals(IMG_TAG)) {
                return generateImageAlternativeText(element);
            }
        }
        return "";
    }

    private String generateImageAlternativeText(Element element) {
        return Optional.ofNullable(element.attributes().get(ALT_TAG))
            .map(StringUtils::normalizeSpace)
            .filter(s -> !s.isEmpty())
            .map(s -> "[" + s + "]")
            .orElse("");
    }

    private String convertListElement(int nestedLevel) {
        if (nestedLevel == 0) {
            return "\n\n";
        } else {
            return "";
        }
    }

    Stream<HTMLNode> flatten(Node base, int listNestedLevel) {
        Position position = getPosition(base);
        int nextElementLevel = getNewNestedLevel(listNestedLevel, base);

        Stream<HTMLNode> baseStream = Stream.of(new HTMLNode(base, listNestedLevel));
        Stream<HTMLNode> flatChildren = base.childNodes()
            .stream()
            .flatMap(node -> flatten(node, nextElementLevel));
        
        switch (position) {
            case PREFIX:
                return Stream.concat(baseStream, flatChildren);
            case SUFFIX:
                return Stream.concat(flatChildren, baseStream);
            default:
                throw new RuntimeException("Unexpected POSITION for node element: " + position);
        }
    }

    private int getNewNestedLevel(int listNestedLevel, Node node) {
        if (node instanceof Element) {
            Element element = (Element) node;
            if (isList(element)) {
                return listNestedLevel + 1;
            }
        }
        return listNestedLevel;
    }

    private boolean isList(Element element) {
        return element.tagName().equals(UL_TAG) || element.tagName().equals(OL_TAG);
    }

    private enum Position {
        PREFIX,
        SUFFIX
    }

    private Position getPosition(Node node) {
        if (node instanceof Element) {
            Element element = (Element) node;
            if (element.tagName().equals(LI_TAG)) {
                return Position.PREFIX;
            }
        }
        return Position.SUFFIX;
    }

    private static class HTMLNode {
        private final Node underlyingNode;
        private final int listNestedLevel;

        public HTMLNode(Node underlyingNode, int listNestedLevel) {
            this.underlyingNode = underlyingNode;
            this.listNestedLevel = listNestedLevel;
        }
    }

}
