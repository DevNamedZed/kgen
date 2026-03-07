package org.kgen.examples.lang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LexerTest {

    @Test
    fun emptyInput() {
        val tokens = Lexer("").tokenize()
        assertEquals(1, tokens.size)
        assertEquals(Token.Kind.EOF, tokens[0].kind)
    }

    @Test
    fun intLiteral() {
        val tokens = Lexer("42").tokenize()
        assertEquals(Token.Kind.INT_LIT, tokens[0].kind)
        assertEquals("42", tokens[0].text)
    }

    @Test
    fun floatLiteral() {
        val tokens = Lexer("3.14").tokenize()
        assertEquals(Token.Kind.FLOAT_LIT, tokens[0].kind)
        assertEquals("3.14", tokens[0].text)
    }

    @Test
    fun stringLiteral() {
        val tokens = Lexer("\"hello\"").tokenize()
        assertEquals(Token.Kind.STRING_LIT, tokens[0].kind)
        assertEquals("hello", tokens[0].text)
    }

    @Test
    fun keywords() {
        val tokens = Lexer("fun var val if else while return true false").tokenize()
        assertEquals(Token.Kind.FUN, tokens[0].kind)
        assertEquals(Token.Kind.VAR, tokens[1].kind)
        assertEquals(Token.Kind.VAL, tokens[2].kind)
        assertEquals(Token.Kind.IF, tokens[3].kind)
        assertEquals(Token.Kind.ELSE, tokens[4].kind)
        assertEquals(Token.Kind.WHILE, tokens[5].kind)
        assertEquals(Token.Kind.RETURN, tokens[6].kind)
        assertEquals(Token.Kind.TRUE, tokens[7].kind)
        assertEquals(Token.Kind.FALSE, tokens[8].kind)
    }

    @Test
    fun typeKeywords() {
        val tokens = Lexer("int float bool string void").tokenize()
        assertEquals(Token.Kind.INT, tokens[0].kind)
        assertEquals(Token.Kind.FLOAT, tokens[1].kind)
        assertEquals(Token.Kind.BOOL, tokens[2].kind)
        assertEquals(Token.Kind.STRING, tokens[3].kind)
        assertEquals(Token.Kind.VOID, tokens[4].kind)
    }

    @Test
    fun operators() {
        val tokens = Lexer("+ - * / % == != < > <= >= = && ||").tokenize()
        assertEquals(Token.Kind.PLUS, tokens[0].kind)
        assertEquals(Token.Kind.MINUS, tokens[1].kind)
        assertEquals(Token.Kind.STAR, tokens[2].kind)
        assertEquals(Token.Kind.SLASH, tokens[3].kind)
        assertEquals(Token.Kind.PERCENT, tokens[4].kind)
        assertEquals(Token.Kind.EQ, tokens[5].kind)
        assertEquals(Token.Kind.NEQ, tokens[6].kind)
        assertEquals(Token.Kind.LT, tokens[7].kind)
        assertEquals(Token.Kind.GT, tokens[8].kind)
        assertEquals(Token.Kind.LTE, tokens[9].kind)
        assertEquals(Token.Kind.GTE, tokens[10].kind)
        assertEquals(Token.Kind.ASSIGN, tokens[11].kind)
        assertEquals(Token.Kind.AND, tokens[12].kind)
        assertEquals(Token.Kind.OR, tokens[13].kind)
    }

    @Test
    fun delimiters() {
        val tokens = Lexer("( ) { } , : ;").tokenize()
        assertEquals(Token.Kind.LPAREN, tokens[0].kind)
        assertEquals(Token.Kind.RPAREN, tokens[1].kind)
        assertEquals(Token.Kind.LBRACE, tokens[2].kind)
        assertEquals(Token.Kind.RBRACE, tokens[3].kind)
        assertEquals(Token.Kind.COMMA, tokens[4].kind)
        assertEquals(Token.Kind.COLON, tokens[5].kind)
        assertEquals(Token.Kind.SEMICOLON, tokens[6].kind)
    }

    @Test
    fun comments() {
        val tokens = Lexer("42 // this is a comment\n7").tokenize()
        assertEquals(2, tokens.size - 1) // minus EOF
        assertEquals("42", tokens[0].text)
        assertEquals("7", tokens[1].text)
    }

    @Test
    fun annotations() {
        val tokens = Lexer("@native fun f(): void {}").tokenize()
        assertEquals(Token.Kind.NATIVE, tokens[0].kind)
    }

    @Test
    fun functionSignature() {
        val tokens = Lexer("fun add(a: int, b: int): int { return a + b }").tokenize()
        assertEquals(Token.Kind.FUN, tokens[0].kind)
        assertEquals(Token.Kind.IDENT, tokens[1].kind)
        assertEquals("add", tokens[1].text)
    }

    @Test
    fun lineTracking() {
        val tokens = Lexer("a\nb\nc").tokenize()
        assertEquals(1, tokens[0].line)
        assertEquals(2, tokens[1].line)
        assertEquals(3, tokens[2].line)
    }

    @Test
    fun colTracking() {
        val tokens = Lexer("ab cd").tokenize()
        assertEquals(1, tokens[0].col)
        assertEquals(4, tokens[1].col)
    }

    @Test
    fun externKeyword() {
        val tokens = Lexer("extern").tokenize()
        assertEquals(Token.Kind.EXTERN, tokens[0].kind)
    }

    @Test
    fun nativeKeyword() {
        val tokens = Lexer("native").tokenize()
        assertEquals(Token.Kind.NATIVE, tokens[0].kind)
    }

    @Test
    fun managedKeyword() {
        val tokens = Lexer("managed").tokenize()
        assertEquals(Token.Kind.MANAGED, tokens[0].kind)
    }

    @Test
    fun arrowOperator() {
        val tokens = Lexer("->").tokenize()
        assertEquals(Token.Kind.ARROW, tokens[0].kind)
    }

    @Test
    fun notOperator() {
        val tokens = Lexer("not").tokenize()
        assertEquals(Token.Kind.NOT, tokens[0].kind)
    }

    @Test
    fun andOrKeywords() {
        val tokens = Lexer("and or").tokenize()
        assertEquals(Token.Kind.AND, tokens[0].kind)
        assertEquals(Token.Kind.OR, tokens[1].kind)
    }

    @Test
    fun stringWithEscapes() {
        val tokens = Lexer("\"hello\\nworld\"").tokenize()
        assertEquals(Token.Kind.STRING_LIT, tokens[0].kind)
        assertEquals("hello\nworld", tokens[0].text)
    }

    @Test
    fun stringWithTab() {
        val tokens = Lexer("\"a\\tb\"").tokenize()
        assertEquals("a\tb", tokens[0].text)
    }

    @Test
    fun stringWithQuote() {
        val tokens = Lexer("\"say \\\"hi\\\"\"").tokenize()
        assertEquals("say \"hi\"", tokens[0].text)
    }

    @Test
    fun stringWithBackslash() {
        val tokens = Lexer("\"path\\\\file\"").tokenize()
        assertEquals("path\\file", tokens[0].text)
    }

    @Test
    fun unterminatedStringThrows() {
        assertThrows(LangError::class.java) {
            Lexer("\"no end").tokenize()
        }
    }

    @Test
    fun unexpectedCharThrows() {
        assertThrows(LangError::class.java) {
            Lexer("~").tokenize()
        }
    }

    @Test
    fun multipleComments() {
        val tokens = Lexer("// comment 1\n42\n// comment 2\n7").tokenize()
        assertEquals(2, tokens.size - 1) // 42, 7
    }

    @Test
    fun identifierWithUnderscore() {
        val tokens = Lexer("my_var _x a1b2").tokenize()
        assertEquals(Token.Kind.IDENT, tokens[0].kind)
        assertEquals("my_var", tokens[0].text)
        assertEquals("_x", tokens[1].text)
        assertEquals("a1b2", tokens[2].text)
    }

    @Test
    fun largeIntLiteral() {
        val tokens = Lexer("999999999").tokenize()
        assertEquals("999999999", tokens[0].text)
    }

    @Test
    fun floatWithManyDecimals() {
        val tokens = Lexer("3.141592653").tokenize()
        assertEquals(Token.Kind.FLOAT_LIT, tokens[0].kind)
        assertEquals("3.141592653", tokens[0].text)
    }

    @Test
    fun tokenToString() {
        val token = Token(Token.Kind.INT_LIT, "42", 1, 1)
        assertEquals("INT_LIT(42)", token.toString())
    }

    @Test
    fun emptyStringLiteral() {
        val tokens = Lexer("\"\"").tokenize()
        assertEquals(Token.Kind.STRING_LIT, tokens[0].kind)
        assertEquals("", tokens[0].text)
    }

    @Test
    fun bangIsNot() {
        val tokens = Lexer("!").tokenize()
        assertEquals(Token.Kind.NOT, tokens[0].kind)
    }

    @Test
    fun whitespaceBetweenTokens() {
        val tokens = Lexer("  42  \t  7  ").tokenize()
        assertEquals(2, tokens.size - 1)
    }
}
