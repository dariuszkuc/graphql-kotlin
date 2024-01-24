package com.expediagroup.graphql.examples.server.spring.directives

import com.expediagroup.graphql.generator.annotations.GraphQLDirective
import graphql.Scalars
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLNonNull

@GraphQLDirective(name = "custom")
annotation class CustomDirective(
    val name: String,
    val customObject: CustomObject
)

annotation class CustomObject(val first: String, val second: Int)

val CUSTOM_OBJECT_INPUT_TYPE: GraphQLInputObjectType = GraphQLInputObjectType.newInputObject()
    .name("CustomInputObject")
    .field(
        GraphQLInputObjectField.newInputObjectField()
            .name("first")
            .type(GraphQLNonNull.nonNull(Scalars.GraphQLString))
            .build()
    )
    .field(
        GraphQLInputObjectField.newInputObjectField()
            .name("second")
            .type(GraphQLNonNull.nonNull(Scalars.GraphQLString))
            .build()
    )
    .build()
