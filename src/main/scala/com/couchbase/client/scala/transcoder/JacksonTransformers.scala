/**
  * Copyright (C) 2015 Couchbase, Inc.
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in
  * all copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
  * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
  * IN THE SOFTWARE.
  */
package com.couchbase.client.scala.transcoder

import com.couchbase.client.scala.document.json.{JsonArray, JsonObject}
import com.fasterxml.jackson.core.{JsonToken, JsonParser, JsonGenerator, Version}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

class JacksonTransformers {

}

object JacksonTransformers {

  val MAPPER = new ObjectMapper()

  private val JSON_VALUE_MODULE = new SimpleModule("JsonValueModule",
    new Version(1, 0, 0, null, null, null))

  JSON_VALUE_MODULE.addSerializer(classOf[JsonObject], new JsonObjectSerializer)
  JSON_VALUE_MODULE.addSerializer(classOf[JsonArray], new JsonArraySerializer)
  JSON_VALUE_MODULE.addDeserializer(classOf[JsonObject], new JsonObjectDeserializer)
  JSON_VALUE_MODULE.addDeserializer(classOf[JsonArray], new JsonArrayDeserializer)

  MAPPER.registerModules(DefaultScalaModule, JSON_VALUE_MODULE)
}

class JsonObjectSerializer extends JsonSerializer[JsonObject] {
  override def serialize(t: JsonObject, gen: JsonGenerator, prov: SerializerProvider): Unit = {
    gen.writeObject(t.toMap)
  }
}

class JsonArraySerializer extends JsonSerializer[JsonArray] {
  override def serialize(t: JsonArray, gen: JsonGenerator, prov: SerializerProvider): Unit = {
    gen.writeObject(t.toList)
  }
}

abstract class AbstractJsonValueDeserializer[T] extends JsonDeserializer[T] {
  def decodeJsonObject(parser: JsonParser, target: JsonObject): JsonObject = {
    var current = parser.nextToken()
    var field: String = null

    while(current != null && current != JsonToken.END_OBJECT) {
      if (current == JsonToken.FIELD_NAME) {
        field = parser.getCurrentName
      } else {
        target.put(field, decodeToken(current, parser))
      }
      current = parser.nextToken()
    }

    target
  }

  def decodeJsonArray(parser: JsonParser, target: JsonArray): JsonArray = {
    var current = parser.nextToken()
    while (current != null && current != JsonToken.END_ARRAY) {
      target += decodeToken(current, parser)
      current = parser.nextToken()
    }
    target
  }

  def decodeToken(token: JsonToken, parser: JsonParser): Any = {
    token match {
      case JsonToken.START_OBJECT => decodeJsonObject(parser, JsonObject())
      case JsonToken.START_ARRAY => decodeJsonArray(parser, JsonArray())
      case JsonToken.VALUE_TRUE | JsonToken.VALUE_FALSE => parser.getBooleanValue
      case JsonToken.VALUE_STRING => parser.getValueAsString
      case JsonToken.VALUE_NUMBER_INT => parser.getNumberValue
      case JsonToken.VALUE_NUMBER_FLOAT => parser.getDoubleValue
      case JsonToken.VALUE_NULL => null
      case _ => throw new IllegalStateException("Could not decode JSON token: " + token)
    }
  }

}

class JsonArrayDeserializer extends AbstractJsonValueDeserializer[JsonArray] {
  override def deserialize(parser: JsonParser, ctx: DeserializationContext): JsonArray = {
    if (parser.getCurrentToken == JsonToken.START_ARRAY) {
      decodeJsonArray(parser, JsonArray())
    } else {
      throw new IllegalStateException("Expecting Array as root level object, " +
        "was: " + parser.getCurrentToken)
    }
  }
}

class JsonObjectDeserializer extends AbstractJsonValueDeserializer[JsonObject] {
  override def deserialize(parser: JsonParser, ctx: DeserializationContext): JsonObject = {
    if (parser.getCurrentToken == JsonToken.START_OBJECT) {
      decodeJsonObject(parser, JsonObject())
    } else {
      throw new IllegalStateException("Expecting Object as root level object, " +
        "was: " + parser.getCurrentToken)
    }
  }
}