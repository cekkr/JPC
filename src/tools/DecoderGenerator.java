package tools;

import java.io.*;
import java.util.*;

import org.jpc.emulator.execution.decoder.*;

public class DecoderGenerator
{
    public static String args = "blockStart, eip, prefices, input";
    public static String argsDef = "int blockStart, int eip, int prefices, PeekableInputStream input";

    public static byte[] EMPTY = new byte[28];

    public static class OpcodeHolder
    {
        Map<Instruction, byte[]> myops = new HashMap();
        List<String> names = new ArrayList();
        Set<String> namesSet = new HashSet();
        private int modeType;

        public OpcodeHolder(int modeType)
        {
            this.modeType = modeType;
        }

        public void addOpcode(Instruction in, byte[] raw)
        {
            String name = Disassembler.getExecutableName(modeType, in);
            //try {
            //    if (names.contains(name) && !name.contains("Unimplemented")&& !name.contains("Illegal"))
            //        return;
            //} catch (Exception s) {return;}
            names.add(name);
            namesSet.add(name);
            myops.put(in, raw);
        }

        public Map<Instruction, byte[]> getReps()
        {
            Map<Instruction, byte[]> reps = new HashMap();
            for (Instruction in: myops.keySet())
                if (myops.get(in)[0] == (byte)0xF3)
                    reps.put(in, myops.get(in));
            return reps;
        }

        public Map<Instruction, byte[]> getRepnes()
        {
            Map<Instruction, byte[]> reps = new HashMap();
            for (Instruction in: myops.keySet())
                if (myops.get(in)[0] == (byte)0xF2)
                    reps.put(in, myops.get(in));
            return reps;
        }

        public Map<Instruction, byte[]> getNonreps()
        {
            Map<Instruction, byte[]> reps = new HashMap();
            for (Instruction in: myops.keySet())
                if ((myops.get(in)[0] != (byte)0xF2) && (myops.get(in)[0] != (byte)0xF3))
                    reps.put(in, myops.get(in));
            return reps;
        }

        public boolean hasReps()
        {
            for (String opname: namesSet)
                if (opname.contains("rep"))
                    return true;
            return false;
        }

        public boolean hasUnimplemented()
        {
            for (String name: names)
                if (name.contains("Unimplemented"))
                    return true;
            return false;
        }

        public boolean allUnimplemented()
        {
            for (String name: names)
                if (!name.contains("Unimplemented"))
                    return false;
            return true;
        }

        public boolean isMem()
        {
            if (namesSet.size() > 2)
                return false;
            String name = null;
            for (String s: namesSet)
            {
                if (name == null)
                    name = s;
                else if ((name + "_mem").equals(s))
                    return true;
                else if ((s+"_mem").equals(name))
                    return true;
            }
            return false;
        }

        public String toString()
        {
            if (namesSet.size() == 0)
                return "null;";

            StringBuilder b = new StringBuilder();
            if (namesSet.size() == 1)
            {
                b.append(new SingleOpcode(names.get(0)));
            }
            else if (isMem())
            {
                String name = null;
                for (String n: namesSet)
                {
                    if (name == null)
                        name = n;
                    else if (name.length() > n.length())
                        name = n;
                }
                b.append(new MemoryChooser(name));
            }
            else
            {
                if (allUnimplemented())
                {
                    b.append(new SingleOpcode(names.get(0)));
                }
                else
                {
                    b.append(new RepChooser(getReps(), getRepnes(), getNonreps(), modeType));
                }
            }
            return b.toString().trim();
        }
    }

    public static class DecoderTemplate
    {
        public void writeStart(StringBuilder b)
        {
            b.append("new OpcodeDecoder() {\n    public Executable decodeOpcode("+argsDef+") {\n");
        }

        public void writeBody(StringBuilder b)
        {
            b.append("throw new IllegalStateException(\"Unimplemented Opcode\");");
        }

        public void writeEnd(StringBuilder b)
        {
            b.append("    }\n};\n");
        }

        public String toString()
        {
            StringBuilder b = new StringBuilder();
            writeStart(b);
            writeBody(b);
            writeEnd(b);
            return b.toString();
        }
    }

    public static class SingleOpcode extends DecoderTemplate
    {
        String classname;

        public SingleOpcode(String name)
        {
            this.classname = name;
        }

        public void writeBody(StringBuilder b)
        {
            b.append("        return new "+classname + "("+args+");\n");
        }
    }

    public static class RepChooser extends DecoderTemplate
    {
        Map<Instruction, byte[]> reps;
        Map<Instruction, byte[]> repnes;
        Map<Instruction, byte[]> normals;
        int mode;

        public RepChooser(Map<Instruction, byte[]> reps, Map<Instruction, byte[]> repnes, Map<Instruction, byte[]> normals, int mode)
        {
            this.reps = reps;
            this.repnes = repnes;
            this.normals = normals;
            this.mode = mode;
        }

        public void writeBody(StringBuilder b)
        {
            Set<String> repNames = new HashSet<String>();
            for (Instruction in: reps.keySet())
                repNames.add(Disassembler.getExecutableName(mode, in));
            Set<String> repneNames = new HashSet<String>();
            for (Instruction in: repnes.keySet())
                repneNames.add(Disassembler.getExecutableName(mode, in));
            Set<String> normalNames = new HashSet<String>();
            for (Instruction in: normals.keySet())
                normalNames.add(Disassembler.getExecutableName(mode, in));

            // only add rep clauses if rep name sets are different to normal name set
            if (!normalNames.containsAll(repneNames))
                if (repnes.size() > 0)
                {
                    b.append("        if (Prefices.isRepne(prefices))\n        {\n");
                    genericChooser(repnes, mode, b);
                    b.append("        }\n");
                }
            if (!normalNames.containsAll(repNames))
                if (reps.size() > 0)
                {
                    b.append("        if (Prefices.isRep(prefices))\n        {\n");
                    genericChooser(reps, mode, b);
                    b.append("        }\n");
                }
            genericChooser(normals, mode, b);
        }
    }

    public static void genericChooser(Map<Instruction, byte[]> ops, int mode, StringBuilder b)
    {
        if (ops.size() == 0)
            return;
        if (ops.size() == 1)
        {
            for (Instruction in: ops.keySet())
            {
                String name = Disassembler.getExecutableName(mode, in);
                b.append("            return new "+name+"("+args+");\n");
            }
            return;
        }
        int differentIndex = 0;
        byte[][] bs = new byte[ops.size()][];
        int index = 0;
        for (byte[] bytes: ops.values())
            bs[index++] = bytes;
        boolean same = true;
        while (same)
        {
            byte elem = bs[0][differentIndex];
            for (int i=1; i < bs.length; i++)
                if (bs[i][differentIndex] != elem)
                {
                    same = false;
                    break;
                }
            if (same)
                differentIndex++;
        }
        // if all names are the same, collapse to 1
        String prevname = null;
        boolean allSameName = true;
        for (Instruction in: ops.keySet())
        {
            String name = Disassembler.getExecutableName(mode, in);
            if (prevname == null)
                prevname = name;
            else if (prevname.equals(name))
                continue;
            else
            {
                allSameName = false;
                break;
            }
        }
        if (allSameName)
        {
            b.append("        return new "+prevname+"("+args+");\n");
        }
        else
        {
            String[] cases = new String[ops.size()];
            int i = 0;
            for (Instruction in: ops.keySet())
            {
                String name = Disassembler.getExecutableName(mode, in);
                cases[i++]=String.format("            case 0x%02x", ops.get(in)[differentIndex])+": return new "+name+"("+args+");\n";
            }
            b.append("        switch (input.peek()) {\n");
            Arrays.sort(cases);
            for (String line: cases)
                b.append(line);
            b.append("        }\n        return null;\n");
        }
    }

    public static class MemoryChooser extends DecoderTemplate
    {
        String name;

        public MemoryChooser(String name)
        {
            this.name = name;
        }

        public void writeBody(StringBuilder b)
        {
            b.append("        if (Modrm.isMem(input.peek()))\n            return new "+name+"_mem("+args+");\n        else\n            return new "+name+"("+args+");\n");
        }
    }

    public static void generate()
    {
        System.out.println("package org.jpc.emulator.execution.decoder;\n");
        System.out.println("import org.jpc.emulator.execution.*;");
        System.out.println("import org.jpc.emulator.execution.opcodes.rm.*;");
        System.out.println("import org.jpc.emulator.execution.opcodes.pm.*;");
        System.out.println("import org.jpc.emulator.execution.opcodes.vm.*;\n");
        System.out.println("public class ExecutableTables {");
        System.out.println("    public static void populateRMOpcodes(OpcodeDecoder[] ops) {");

        OpcodeHolder[] rmops = new OpcodeHolder[0x800];
        for (int i=0; i < rmops.length; i++)
            rmops[i] = new OpcodeHolder(1);
        generateRep(16, rmops);
        for (int i=0; i < rmops.length; i++)
            System.out.printf("ops[0x%02x] = "+rmops[i]+"\n", i);
        System.out.println("}\n\n    public static void populatePMOpcodes(OpcodeDecoder[] ops) {\n");

        OpcodeHolder[] pmops = new OpcodeHolder[0x800];
        for (int i=0; i < pmops.length; i++)
            pmops[i] = new OpcodeHolder(2);
        generateRep(32, pmops);
        for (int i=0; i < pmops.length; i++)
            System.out.printf("ops[0x%02x] = "+pmops[i]+"\n", i);
        System.out.println("}\n\n    public static void populateVMOpcodes(OpcodeDecoder[] ops) {\n");

        OpcodeHolder[] vmops = new OpcodeHolder[0x800];
        for (int i=0; i < vmops.length; i++)
            vmops[i] = new OpcodeHolder(3);
        generateRep(16, vmops);
        for (int i=0; i < vmops.length; i++)
            System.out.printf("ops[0x%02x] = "+vmops[i]+"\n", i);
        System.out.println("}\n}\n");
    }

    public static void generateRep(int mode, OpcodeHolder[] ops)
    {
        byte[] x86 = new byte[28];
        generateMode(mode, x86, 0, ops);
        x86[0] = (byte)0xF2;
        generateMode(mode, x86, 1, ops);
        x86[0] = (byte)0xF3;
        generateMode(mode, x86, 1, ops);
    }

    public static void generateMode(int mode, byte[] x86, int opbyte, OpcodeHolder[] ops)
    {
        Disassembler.ByteArrayPeekStream input = new Disassembler.ByteArrayPeekStream(x86);

        int originalOpbyte = opbyte;
        int base = 0;
        for (int k=0; k <2; k++) // addr
        {
            for (int j=0; j <2; j++) // op size
            {
                for (int i=0; i < 2; i++) // 0F opcode start
                {
                    for (int opcode = 0; opcode < 256; opcode++)
                    {
                        if (Prefices.isPrefix(opcode))
                            continue;
                        if ((opcode == 0x0f) && ((base & 0x100) == 0))
                            continue;
                        // fill x86 with appropriate bytes
                        x86[opbyte] = (byte)opcode;
                        input.resetCounter();

                        // decode prefices
                        Instruction in = new Instruction();
                        Disassembler.get_prefixes(mode, input, in);
                        int preficesLength = input.getCounter();

                        int opcodeLength;
                        try {
                            // decode opcode part
                            Disassembler.search_table(mode, input, in);
                            Disassembler.do_mode(mode, in);
                            opcodeLength = input.getCounter() - preficesLength;

                            // decode operands
                            Disassembler.disasm_operands(mode, input, in);
                            Disassembler.resolve_operator(mode, input, in);
                        } catch (IllegalStateException s) {continue;}
                        int argumentsLength = input.getCounter()-opcodeLength-preficesLength;
                        String[] args = in.getArgsTypes();
                        if (argumentsLength == 0)
                        {
                            // single byte opcode
                            ops[base + opcode].addOpcode(in, x86.clone());
                        }
                        else if ((args.length == 1) && (immediates.contains(args[0])))
                        {
                            // don't enumerate immediates
                            ops[base + opcode].addOpcode(in, x86.clone());
                        }
                        else
                        {
                            // enumerate modrm
                            for (int modrm = 0; modrm < 256; modrm++)
                            {
                                input.resetCounter();
                                x86[opbyte+1] = (byte)modrm;
                                Instruction modin = new Instruction();
                                try {

                                    Disassembler.get_prefixes(mode, input, modin);
                                    Disassembler.search_table(mode, input, modin);
                                    Disassembler.do_mode(mode, modin);
                                    Disassembler.disasm_operands(mode, input, modin);
                                    Disassembler.resolve_operator(mode, input, modin);
                                } catch (IllegalStateException s)
                                {
                                    x86[opbyte+1] = 0;
                                    continue;
                                }
                                ops[base + opcode].addOpcode(modin, x86.clone());
                            }
                            x86[opbyte+1] = 0;
                        }
                    }
                    System.arraycopy(EMPTY, opbyte, x86, opbyte, x86.length-opbyte);
                    x86[opbyte++] = 0x0f;
                    base += 0x100; // now do the 0x0f opcodes (2 byte opcodes)
                }

                if (x86[originalOpbyte] == (byte)0x67)
                    opbyte = originalOpbyte + 1;
                else
                    opbyte = originalOpbyte;
                System.arraycopy(EMPTY, opbyte, x86, opbyte, x86.length-opbyte);
                x86[opbyte++] = 0x66;
            }
            System.arraycopy(EMPTY, originalOpbyte, x86, originalOpbyte, x86.length -originalOpbyte);
            x86[originalOpbyte] = 0x67;
            opbyte = originalOpbyte + 1;
        }
    }

    public static List<String> immediates = Arrays.asList(new String[]{"Jb", "Jw", "Jd", "Ib", "Iw", "Id"});
}
