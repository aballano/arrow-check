package arrow.check.property

import arrow.Kind
import arrow.check.gen.Fun
import arrow.check.gen.Gen
import arrow.check.gen.GenT
import arrow.check.gen.GenTOf
import arrow.check.gen.GenTPartialOf
import arrow.check.gen.MonadGen
import arrow.check.gen.fix
import arrow.check.gen.monadGen
import arrow.check.property.instances.PropertyTMonadTest
import arrow.check.property.instances.monad
import arrow.check.property.instances.monadTrans
import arrow.core.Either
import arrow.core.ForId
import arrow.fx.IO
import arrow.fx.IOPartialOf
import arrow.fx.extensions.io.monad.monad
import arrow.typeclasses.Monad
import arrow.typeclasses.MonadContinuation
import arrow.typeclasses.MonadSyntax
import arrow.typeclasses.Show
import pretty.Doc
import kotlin.coroutines.startCoroutine

fun property(propertyConfig: PropertyConfig = PropertyConfig(), c: suspend PropertyTestSyntax.() -> Unit): Property {
    val continuation = PropertyTestContinuation<Unit>()
    val wrapReturn: suspend PropertyTestContinuation<*>.() -> PropertyT<IOPartialOf<Nothing>, Unit> = {
        // Until https://github.com/arrow-kt/arrow/issues/1976 has a good fix
        unit().bind()
        just(c()).fix()
    }
    wrapReturn.startCoroutine(continuation, continuation)
    return Property(
        config = propertyConfig,
        prop = continuation.returnedMonad().fix()
    )
}

interface PropertyTestSyntax : MonadSyntax<PropertyTPartialOf<IOPartialOf<Nothing>>>,
    PropertyTest<IOPartialOf<Nothing>> {
    override fun MM(): Monad<IOPartialOf<Nothing>> = IO.monad()

    suspend fun <A> IO<Nothing, A>.bind(): A = PropertyT.monadTrans().run { liftT(IO.monad()) }.bind()
}

class PropertyTestContinuation<A> : MonadContinuation<PropertyTPartialOf<IOPartialOf<Nothing>>, A>(
    PropertyT.monad(IO.monad())
), PropertyTestSyntax {
    override fun <A> just(a: A): Kind<PropertyTPartialOf<IOPartialOf<Nothing>>, A> =
        PropertyT.monad(MM()).just(a)

    override fun <A, B> tailRecM(
        a: A,
        f: (A) -> Kind<PropertyTPartialOf<IOPartialOf<Nothing>>, Either<A, B>>
    ): Kind<PropertyTPartialOf<IOPartialOf<Nothing>>, B> =
        PropertyT.monad(MM()).tailRecM(a, f)

    override fun <A, B> Kind<PropertyTPartialOf<IOPartialOf<Nothing>>, A>.flatMap(f: (A) -> Kind<PropertyTPartialOf<IOPartialOf<Nothing>>, B>): Kind<PropertyTPartialOf<IOPartialOf<Nothing>>, B> =
        PropertyT.monad(MM()).run { flatMap(f) }
}

fun <M> PropertyT.Companion.propertyTestM(MM: Monad<M>): PropertyTest<M> = object : PropertyTest<M> {
    override fun MM(): Monad<M> = MM
}

interface PropertyTest<M> : PropertyTMonadTest<M> {
    override fun MM(): Monad<M>

    // forall should give implicit access to MonadTest with the right monad
    //  id for all non-t variants and M in all other cases
    fun <A> forAllWithT(showA: (A) -> Doc<Markup>, gen: GenT<M, A>): PropertyT<M, A> =
        forAllWithT(showA, gen, MM())

    fun <A> forAllWith(showA: (A) -> Doc<Markup>, gen: Gen<A>): PropertyT<M, A> =
        forAllWith(showA, gen, MM())

    fun <A> forAllT(gen: GenT<M, A>, SA: Show<A> = Show.any()): PropertyT<M, A> =
        forAllT(gen, MM(), SA)

    fun <A> forAll(gen: Gen<A>, SA: Show<A> = Show.any()): PropertyT<M, A> =
        forAll(gen, MM(), SA)

    fun <A, B> forAllFn(
        gen: Gen<Fun<A, B>>,
        SA: Show<A> = Show.any(),
        SB: Show<B> = Show.any()
    ): PropertyT<M, (A) -> B> =
        forAllFn(gen, MM(), SA, SB)

    fun <A> forAllWithT(showA: (A) -> Doc<Markup>, f: MonadGen<GenTPartialOf<M>, M>.() -> GenTOf<M, A>) =
        forAllWithT(showA, GenT.monadGen(MM()).f().fix())

    fun <A> forAllWith(showA: (A) -> Doc<Markup>, f: MonadGen<GenTPartialOf<ForId>, ForId>.() -> GenTOf<ForId, A>) =
        forAllWith(showA, GenT.monadGen().f().fix())

    fun <A> forAllT(SA: Show<A> = Show.any(), f: MonadGen<GenTPartialOf<M>, M>.() -> GenTOf<M, A>) =
        forAllT(GenT.monadGen(MM()).f().fix(), SA)

    fun <A> forAll(SA: Show<A> = Show.any(), f: MonadGen<GenTPartialOf<ForId>, ForId>.() -> GenTOf<ForId, A>) =
        forAll(GenT.monadGen().f().fix(), SA)

    fun <A, B> forAllFn(
        SA: Show<A> = Show.any(),
        SB: Show<B> = Show.any(),
        f: MonadGen<GenTPartialOf<ForId>, ForId>.() -> GenTOf<ForId, Fun<A, B>>
    ) =
        forAllFn(GenT.monadGen().f().fix(), SA, SB)

    fun <A> discard(): PropertyT<M, A> = discard(MM())
}
