/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.example.json;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;

/**
 * JsonUtils<BR>
 * Serializes class name in the @class attribute in JSON.<BR>
 * Deserializes by @class attribute.
 */
public class JsonUtils {
	private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);
	
	private static final StdTypeResolverBuilder typeResolverBuilder =
			new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE)
			.init(JsonTypeInfo.Id.CLASS, null)
			.inclusion(JsonTypeInfo.As.PROPERTY)
			.typeProperty("@class");
	
	/**
	 * JSON string to Java fact.
	 * 
	 * @param json JSON String
	 * @param clazz target class
	 * @return deserialized Java fact
	 * @throws IOException
	 * @throws JsonMappingException
	 * @throws JsonParseException
	 */
	static public <T> T json2Fact(String json, Class<T> clazz) throws IOException, JsonMappingException, JsonParseException {
		ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.setDefaultTyping(typeResolverBuilder);
        return mapper.readValue(json, clazz);
	}

	/**
	 * Java fact to JSON string.
	 * @param fact Java fact
	 * @return JSON String
	 * @throws JsonProcessingException
	 */
	static public String fact2Json(Object fact) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		if (logger.isDebugEnabled()) {
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
		}
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.setDefaultTyping(typeResolverBuilder);
		return mapper.writeValueAsString(fact);
	}
}
