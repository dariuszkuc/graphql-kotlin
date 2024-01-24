/*
 * Copyright 2023 Expedia, Inc
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

package com.expediagroup.graphql.examples.server.spring.hooks

import com.expediagroup.graphql.examples.server.spring.directives.CUSTOM_OBJECT_INPUT_TYPE
import com.expediagroup.graphql.examples.server.spring.directives.CustomObject
import com.expediagroup.graphql.examples.server.spring.model.MyValueClass
import com.expediagroup.graphql.generator.directives.DirectiveMetaInformation
import com.expediagroup.graphql.generator.directives.KotlinDirectiveWiringFactory
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.Scalars
import graphql.language.IntValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import org.springframework.beans.factory.BeanFactoryAware
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties

/**
 * Schema generator hook that adds additional scalar types.
 */
class CustomSchemaGeneratorHooks(override val wiringFactory: KotlinDirectiveWiringFactory) : SchemaGeneratorHooks {

    /**
     * Register additional GraphQL scalar types.
     */
    override fun willGenerateGraphQLType(type: KType): GraphQLType? = when (type.classifier) {
        MyValueClass::class -> Scalars.GraphQLString
        UUID::class -> graphqlUUIDType
        ClosedRange::class -> {
            when (type.arguments[0].type?.classifier as? KClass<*>) {
                LocalDate::class -> graphqlPeriodType
                else -> null
            }
        }
        CustomObject::class -> CUSTOM_OBJECT_INPUT_TYPE
        else -> null
    }

    override fun willApplyDirective(directiveInfo: DirectiveMetaInformation, directive: GraphQLDirective): GraphQLAppliedDirective? {
        return if (directiveInfo.effectiveName == "custom") {
            // we need to manually transform @custom directive definition to handle input object arguments
            val customObject = directiveInfo.directive.annotationClass.memberProperties
                .first { it.name == "customObject" }
                .call(directiveInfo.directive) as CustomObject

            return directive.toAppliedDirective()
                .transform { appliedDirectiveBuilder ->
                    directive.getArgument("name")
                        .toAppliedArgument()
                        .transform { nameArgBuilder ->
                            val nameProp = directiveInfo.directive.annotationClass.memberProperties.first { it.name == "name" }
                            val value = nameProp.call(directiveInfo.directive)
                            nameArgBuilder.valueProgrammatic(value)
                        }
                        .let {
                            appliedDirectiveBuilder.argument(it)
                        }
                    directive.getArgument("customObject")
                        .toAppliedArgument()
                        .transform { argumentBuilder ->
                            argumentBuilder.valueLiteral(
                                ObjectValue(
                                    listOf(
                                        ObjectField("first", StringValue(customObject.first)),
                                        ObjectField("second", IntValue(customObject.second.toBigInteger()))
                                    )
                                )
                            )
                        }
                        .let {
                            appliedDirectiveBuilder.argument(it)
                        }
                }
        } else {
            super.willApplyDirective(directiveInfo, directive)
        }
    }

    /**
     * Register Reactor Mono monad type.
     */
    override fun willResolveMonad(type: KType): KType = when (type.classifier) {
        Mono::class -> type.arguments.first().type ?: type
        Set::class -> List::class.createType(type.arguments)
        else -> type
    }

    /**
     * Exclude the Spring bean factory interface
     */
    override fun isValidSuperclass(kClass: KClass<*>): Boolean {
        return when {
            kClass.isSubclassOf(BeanFactoryAware::class) -> false
            else -> super.isValidSuperclass(kClass)
        }
    }
}

internal val graphqlUUIDType = GraphQLScalarType.newScalar()
    .name("UUID")
    .description("A type representing a formatted java.util.UUID")
    .coercing(UUIDCoercing)
    .build()

private object UUIDCoercing : Coercing<UUID, String> {
    override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): UUID =
        runCatching {
            UUID.fromString(serialize(input, graphQLContext, locale))
        }.getOrElse {
            throw CoercingParseValueException("Expected valid UUID but was $input")
        }

    override fun parseLiteral(input: Value<*>, variables: CoercedVariables, graphQLContext: GraphQLContext, locale: Locale): UUID {
        val uuidString = (input as? StringValue)?.value
        return runCatching {
            UUID.fromString(uuidString)
        }.getOrElse {
            throw CoercingParseLiteralException("Expected valid UUID literal but was $uuidString")
        }
    }

    override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String =
        runCatching {
            dataFetcherResult.toString()
        }.getOrElse {
            throw CoercingSerializeException("Data fetcher result $dataFetcherResult cannot be serialized to a String")
        }
}

internal val graphqlPeriodType: GraphQLScalarType = GraphQLScalarType.newScalar()
    .name("Period")
    .description("""A period of local date to local date, inclusive on both ends i.e. a closed range.""")
    .coercing(PeriodCoercing)
    .build()

typealias Period = ClosedRange<LocalDate>

private object PeriodCoercing : Coercing<Period, String> {
    override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Period =
        runCatching {
            input.toString().parseAsPeriod()
        }.getOrElse {
            throw CoercingParseValueException("Expected valid Period but was $input")
        }

    override fun parseLiteral(input: Value<*>, variables: CoercedVariables, graphQLContext: GraphQLContext, locale: Locale): Period =
        runCatching {
            (input as? StringValue)?.value?.parseAsPeriod() ?: throw CoercingParseLiteralException("Expected valid Period literal but was $input")
        }.getOrElse {
            throw CoercingParseLiteralException("Expected valid Period literal but was $input")
        }

    override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String =
        kotlin.runCatching {
            toString()
        }.getOrElse {
            throw CoercingSerializeException("Data fetcher result $dataFetcherResult cannot be serialized to a String")
        }

    private fun String.parseAsPeriod(): Period = split("..").let {
        if (it.size != 2) error("Cannot parse input $this as Period")
        LocalDate.parse(it[0])..LocalDate.parse(it[1])
    }
}
