/*
 * Copyright 2021 - 2023 d-sch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and,
 * limitations under the License.
 */


package io.github.d_sch.webfluxcustomjacksonstream.common.impl;

import java.io.IOException;
import java.util.Objects;
import java.util.Stack;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonTokenParser {
    private JsonTokenParser.ContextStack contextStack = new JsonTokenParser.ContextStack();
    private JsonParser jsonParser;

    public JsonTokenParser(JsonParser jsonParser) {
        contextStack.setCurrentContext(new RootContext());
        this.jsonParser = jsonParser;
    }

    public JsonTokenParser(JsonParser jsonParser, int ignoreLevel) {
        contextStack.setCurrentContext(new RootContext(ignoreLevel));
        this.jsonParser = jsonParser;
    }

    public JsonNode parseToken(JsonToken nextToken) throws IOException {
        return contextStack.currentContext.tokenContext(nextToken).handleToken(nextToken);
    }

    static class ContextStack {
        Stack<ParserContext> contextStack = new Stack<ParserContext>();
        ParserContext currentContext;

        private void setCurrentContext(ParserContext currentContext) {
            this.currentContext = currentContext;
        } 

        private void pushContext(ParserContext parserContext) {
            contextStack.push(currentContext);
            currentContext = parserContext;
        }

        private void popContext() {
            currentContext = contextStack.pop();
        }

        private ParserContext peekContext() {
            return contextStack.peek();
        }
    }

    interface ParserContext {
         Object getContextAggregate();
         ParserContext tokenContext(JsonToken nextToken)  throws IOException;
         JsonNode handleToken(JsonToken nextToken) throws IOException;
    }

    abstract class ParserContextBase<T extends JsonNode> implements ParserContext {
        T contextJsonNode;

        @Override
        public Object getContextAggregate() {
            return contextJsonNode;
        }

        protected void setContextAggregate(T node) {
            contextJsonNode = node;
        }

        public T getContextJsonNode() {
            return contextJsonNode;
        }

        @Override
        public ParserContext tokenContext(JsonToken nextToken) throws IOException {
            return this;
        };

        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                default ->
                    throw new IllegalStateException(String.format("Unexpected token of type '%s': value: '%s'", nextToken.toString(), jsonParser.getText()));
            }
        };
    }

    class RootContext extends ParserContextBase<JsonNode> {
        final int ignoreLevel;
        int levelIgnored;

        public RootContext() {
            this(0);
        }

        public RootContext(int ignoreLevel) {
            super();
            this.ignoreLevel = ignoreLevel;
            setContextAggregate(JsonNodeFactory.instance.missingNode());
        }

        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                case START_OBJECT -> {
                    if (levelIgnored < ignoreLevel) {
                        levelIgnored++;
                    } else {
                        contextStack.pushContext(new RootObjectContext());
                    }
                }
                case START_ARRAY -> {
                    if (levelIgnored < ignoreLevel) {
                        levelIgnored++;
                    } else {
                        contextStack.pushContext(new RootArrayContext());
                    }
                }
                case END_OBJECT, END_ARRAY -> {
                    if (levelIgnored > 0) {
                        levelIgnored--;
                    }
                }
                default -> {
                    return super.handleToken(nextToken);
                }
            }
            return null;
        }
    }

    abstract class ObjectContext extends ParserContextBase<ObjectNode> {

        public ObjectContext() {
            setContextAggregate(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                case FIELD_NAME -> {
                    contextStack.pushContext(new FieldContext(jsonParser.getValueAsString(), this.getContextJsonNode()));
                }
                default ->
                    super.handleToken(nextToken);
            }
            return null;
        }
    } 

    class RootObjectContext extends ObjectContext {
        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                case END_OBJECT -> {
                    var nextNode = (ObjectNode) contextJsonNode;
                    contextJsonNode = null;
                    contextStack.popContext();
                    return nextNode;
                }
                default -> {
                    return super.handleToken(nextToken);
                }
            }
        }
    }

    class ChildObjectContext extends ObjectContext {
        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                case END_OBJECT -> {
                    var parent = contextStack.peekContext();
                    if (parent instanceof FieldContext fieldContext) {
                        valueNode(fieldContext.getContextJsonNode(), this.contextJsonNode, fieldContext.fieldName);
                    } else if (parent instanceof ArrayContext parentContext) {
                        valueNode(parentContext.getContextJsonNode(), this.contextJsonNode);
                    } else {
                        throw new IllegalStateException(
                            String.format("Unexpected parent context: '%s'", parent.getClass().getSimpleName())
                        );
                    }
                    contextStack.popContext();
                    return null;
                }
                default -> {
                    return super.handleToken(nextToken);
                }
            }
        }
    }

    class FieldContext extends ParserContextBase<ObjectNode> {
        String fieldName;

        public FieldContext(String fieldName, ObjectNode contextNode) {
            this.fieldName = fieldName;
            setContextAggregate(contextNode);
        }

        @Override
        public ParserContext tokenContext(JsonToken nextToken) throws IOException {
            switch(nextToken) {
                //Terminal Token
                case END_OBJECT, END_ARRAY, FIELD_NAME -> {
                    contextStack.popContext();
                    return contextStack.currentContext;
                }
                default -> {
                    return this;
                }
            }
        }

        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                case START_OBJECT -> {
                    contextStack.pushContext(new ChildObjectContext());
                }
                case START_ARRAY -> {
                    contextStack.pushContext(new ChildArrayContext());
                }
                case VALUE_FALSE, VALUE_TRUE -> {
                    valueNode(contextJsonNode, JsonNodeFactory.instance.booleanNode(jsonParser.getBooleanValue()), fieldName);
                    contextStack.popContext();
                }
                case VALUE_NULL -> {
                    valueNode(contextJsonNode, JsonNodeFactory.instance.nullNode(), fieldName);
                    contextStack.popContext();
                }
                case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> {
                    valueNumberNode(contextJsonNode, jsonParser.getNumberType(), fieldName);
                    contextStack.popContext();
                }
                case VALUE_STRING -> {
                    valueNode(contextJsonNode, JsonNodeFactory.instance.textNode(jsonParser.getValueAsString()), fieldName);
                    contextStack.popContext();
                }
                default -> {
                    return super.handleToken(nextToken);
                }
            }
            return null;
        }
    }

    abstract class ArrayContext extends ParserContextBase<ArrayNode> {
        public ArrayContext() {
            setContextAggregate(JsonNodeFactory.instance.arrayNode());
        }

        @Override
        public ArrayNode getContextJsonNode() {
            return contextJsonNode;
        }

        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                case START_OBJECT -> {
                    contextStack.pushContext(new ChildObjectContext());
                }
                case START_ARRAY -> {
                    contextStack.pushContext(new ChildArrayContext());
                }
               case VALUE_FALSE, VALUE_TRUE -> 
                    valueNode(contextJsonNode, JsonNodeFactory.instance.booleanNode(jsonParser.getBooleanValue()));
                case VALUE_NULL -> valueNode(contextJsonNode, JsonNodeFactory.instance.nullNode());
                case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT ->
                    valueNumberNode(contextJsonNode, jsonParser.getNumberType());
                case VALUE_STRING -> valueNode(contextJsonNode, JsonNodeFactory.instance.textNode(jsonParser.getValueAsString()));
                default -> {
                    return super.handleToken(nextToken);
                }
            }
            return null;
        }

    }

    class RootArrayContext extends ArrayContext {

        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                case END_ARRAY -> {
                    var nextNode = contextJsonNode;
                    contextStack.popContext();
                    return nextNode;
                }
                default -> {
                    return super.handleToken(nextToken);
                }
            }
        }
    }

    class ChildArrayContext extends ArrayContext {
        
        @Override
        public JsonNode handleToken(JsonToken nextToken) throws IOException {
            switch (nextToken) {
                case END_ARRAY -> {
                    var parent = contextStack.peekContext();
                    if (parent instanceof FieldContext parentField) {
                        valueNode(parentField.getContextJsonNode(), this.contextJsonNode, parentField.fieldName);
                    } else if (parent instanceof ArrayContext parentArray) {
                        valueNode(parentArray.getContextJsonNode(), this.contextJsonNode);
                    } else {
                        throw new IllegalStateException(
                            String.format("Unexpected parent context: '%s'", parent.getClass().getSimpleName())
                        );
                    }
                    contextStack.popContext();
                }
                default -> {
                    return super.handleToken(nextToken);
                }
            }
            return null;
        }
    }

    private void valueNumberNode(JsonNode jsonNode, NumberType numberType) throws IOException {
        valueNumberNode(jsonNode, numberType, null);
    }

    private void valueNumberNode(JsonNode jsonNode, JsonParser.NumberType numberType, String fieldName) throws IOException {
        JsonNode valueNode;
        switch(numberType) {
            case BIG_DECIMAL -> valueNode = JsonNodeFactory.instance.numberNode(jsonParser.getDecimalValue());
            case BIG_INTEGER -> valueNode = JsonNodeFactory.instance.numberNode(jsonParser.getBigIntegerValue());
            case DOUBLE -> valueNode = JsonNodeFactory.instance.numberNode(jsonParser.getDoubleValue());
            case FLOAT -> valueNode = JsonNodeFactory.instance.numberNode(jsonParser.getFloatValue());
            case INT -> valueNode = JsonNodeFactory.instance.numberNode(jsonParser.getIntValue());
            case LONG -> valueNode = JsonNodeFactory.instance.numberNode(jsonParser.getLongValue());
            default -> throw new IllegalStateException("Unknown number type");
        }
        valueNode(jsonNode, valueNode, fieldName);
    }

    private void valueNode(JsonNode jsonNode, JsonNode valueNode) {
        valueNode(jsonNode, valueNode, null);
    }

    private void valueNode(JsonNode jsonNode, JsonNode valueNode, String fieldName) {
        if (jsonNode instanceof ArrayNode arrayNode) {
            arrayNode.add(valueNode);
        } else if (jsonNode instanceof ObjectNode objectNode) {
            Objects.nonNull(fieldName);
            objectNode.set(fieldName, valueNode);
        }
    }

    public void complete() {
        if (!(contextStack.currentContext instanceof RootContext)) {
            //Todo add context information
            throw new IllegalStateException("Unexpected completition!");
        }
    }

}