package org.kgen.examples.lang

data class Token(val kind: Kind, val text: String, val line: Int, val col: Int) {
    enum class Kind {
        // Literals
        INT_LIT, FLOAT_LIT, STRING_LIT, TRUE, FALSE,

        // Identifiers and keywords
        IDENT, FUN, VAR, VAL, IF, ELSE, WHILE, RETURN, NATIVE, MANAGED, EXTERN,

        // Types
        INT, FLOAT, BOOL, STRING, VOID,

        // Operators
        PLUS, MINUS, STAR, SLASH, PERCENT,
        EQ, NEQ, LT, GT, LTE, GTE,
        AND, OR, NOT,
        ASSIGN,

        // Delimiters
        LPAREN, RPAREN, LBRACE, RBRACE, COMMA, COLON, ARROW, SEMICOLON,

        // Special
        EOF,
    }

    override fun toString(): String = "$kind($text)"
}
