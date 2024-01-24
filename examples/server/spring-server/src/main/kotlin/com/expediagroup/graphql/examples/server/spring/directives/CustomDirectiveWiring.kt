/*
 * Copyright 2021 Expedia, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.expediagroup.graphql.examples.server.spring.directives

import com.expediagroup.graphql.generator.directives.KotlinFieldDirectiveEnvironment
import com.expediagroup.graphql.generator.directives.KotlinSchemaDirectiveWiring
import graphql.language.IntValue
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition

class CustomDirectiveWiring : KotlinSchemaDirectiveWiring {

    override fun onField(environment: KotlinFieldDirectiveEnvironment): GraphQLFieldDefinition {
        val field = environment.element
        val directive = field.getAppliedDirective("custom")
        if (directive != null) {
            val name = directive.getArgument("name").argumentValue.value as String
            val customObject = directive.getArgument("customObject").argumentValue.value as ObjectValue
            val first = customObject.objectFields[0].value as StringValue
            val second = customObject.objectFields[1].value as IntValue

            val newDataFetcher = DataFetcher<Any> { "$name:${first.value}${second.value}" }
            environment.setDataFetcher(newDataFetcher)
        }
        return field
    }
}
