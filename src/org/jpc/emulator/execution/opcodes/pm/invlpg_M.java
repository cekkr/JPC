package org.jpc.emulator.execution.opcodes.pm;

import org.jpc.emulator.execution.*;
import org.jpc.emulator.execution.decoder.*;
import org.jpc.emulator.processor.*;
import org.jpc.emulator.processor.fpu64.*;
import static org.jpc.emulator.processor.Processor.*;

public class invlpg_M extends Executable
{
    final Address op1;

    public invlpg_M(int blockStart, Instruction parent)
    {
        super(blockStart, parent);
        op1 = new Address(parent.operand[0], parent.adr_mode);
    }

    public Branch execute(Processor cpu)
    {
        if (cpu.getCPL() != 0) throw new ProcessorException(ProcessorException.Type.GENERAL_PROTECTION, 0, true);//ProcessorException.GENERAL_PROTECTION_0;
		    cpu.linearMemory.invalidateTLBEntry(op1.get(cpu));
        return Branch.None;
    }

    public boolean isBranch()
    {
        return false;
    }

    public String toString()
    {
        return this.getClass().getName();
    }
}